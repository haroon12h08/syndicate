package com.syndicate.regulatory;

import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.issue.Issue;
import com.syndicate.issue.IssueRepository;
import com.syndicate.issue.IssueStatus;
import com.syndicate.regulatory.dto.BlockingIssueDto;
import com.syndicate.regulatory.dto.ReadinessDto;
import com.syndicate.regulatory.dto.RuleEvaluationDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import com.syndicate.workstream.WorkstreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates a transaction's verified facts against the active SEBI rule registry and derives a
 * readiness verdict. Every step is deterministic — same facts in, same verdict out.
 */
@Service
public class ReadinessService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessService.class);

    /** Used when a transaction has not declared a target filing date yet. */
    private static final int DEFAULT_FILING_HORIZON_DAYS = 90;

    private final RegulatoryRuleRepository ruleRepository;
    private final RuleEvaluationRepository evaluationRepository;
    private final FactRepository factRepository;
    private final IssueRepository issueRepository;
    private final WorkstreamRepository workstreamRepository;
    private final TransactionService transactionService;
    private final RuleExpressionEvaluator expressionEvaluator;

    public ReadinessService(RegulatoryRuleRepository ruleRepository,
                             RuleEvaluationRepository evaluationRepository,
                             FactRepository factRepository,
                             IssueRepository issueRepository,
                             WorkstreamRepository workstreamRepository,
                             TransactionService transactionService,
                             RuleExpressionEvaluator expressionEvaluator) {
        this.ruleRepository = ruleRepository;
        this.evaluationRepository = evaluationRepository;
        this.factRepository = factRepository;
        this.issueRepository = issueRepository;
        this.workstreamRepository = workstreamRepository;
        this.transactionService = transactionService;
        this.expressionEvaluator = expressionEvaluator;
    }

    @Transactional(readOnly = true)
    public ReadinessDto getReadiness(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        Transaction transaction = transactionService.findTransaction(transactionId);
        return buildReadiness(transaction, evaluationRepository.findByTransactionId(transactionId));
    }

    @Transactional
    public ReadinessDto evaluate(UUID transactionId, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);
        List<RuleEvaluation> evaluations = evaluateAll(transaction, caller);
        return buildReadiness(transaction, evaluations);
    }

    /**
     * Re-runs the registry after a fact change. Callers invoke this from an after-commit hook,
     * where the triggering transaction is already completing — joining it would silently discard
     * these writes, so a genuinely new transaction is required. Failures must never break the
     * fact edit that triggered them, so problems are logged rather than propagated.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reevaluateQuietly(UUID transactionId, User caller) {
        try {
            Transaction transaction = transactionService.findTransaction(transactionId);
            evaluateAll(transaction, caller);
        } catch (Exception e) {
            log.warn("Readiness re-evaluation failed for transaction {}: {}", transactionId, e.toString());
        }
    }

    private List<RuleEvaluation> evaluateAll(Transaction transaction, User caller) {
        List<RegulatoryRule> rules = ruleRepository.findByActiveTrueOrderByCodeAsc();
        List<Fact> verifiedFacts = factRepository
                .findByWorkstreamTransactionIdAndStatus(transaction.getId(), FactStatus.VERIFIED);

        Map<String, Double> variables = new HashMap<>();
        variables.put("estimatedFilingDate", (double) estimatedFilingDate(transaction)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());

        Instant evaluatedAt = Instant.now();
        List<RuleEvaluation> results = new ArrayList<>();
        for (RegulatoryRule rule : rules) {
            results.add(evaluateRule(transaction, rule, verifiedFacts, variables, evaluatedAt, caller));
        }
        return results;
    }

    private RuleEvaluation evaluateRule(Transaction transaction, RegulatoryRule rule, List<Fact> verifiedFacts,
                                         Map<String, Double> variables, Instant evaluatedAt, User caller) {
        RuleEvaluation evaluation = evaluationRepository
                .findByTransactionIdAndRuleId(transaction.getId(), rule.getId())
                .orElseGet(() -> evaluationRepository.save(new RuleEvaluation(transaction, rule)));

        RuleFactContext context = new RuleFactContext(verifiedFacts, variables);
        List<Fact> candidates = context.matching(rule.getFactLabelPattern());

        RuleEvaluationStatus status;
        String actualValue = null;
        String detail;

        if (candidates.size() < rule.getMinimumFacts()) {
            status = RuleEvaluationStatus.MISSING_EVIDENCE;
            detail = "Needs at least " + rule.getMinimumFacts() + " verified fact(s) labelled like \""
                    + rule.getFactLabelPattern() + "\"; found " + candidates.size() + ".";
        } else {
            try {
                boolean passed = expressionEvaluator.evaluate(rule.getExpression(), context);
                status = passed ? RuleEvaluationStatus.PASSED : RuleEvaluationStatus.FAILED;
                actualValue = describeActual(candidates);
                detail = passed
                        ? "Satisfied: " + rule.getRequirementSummary()
                        : "Not satisfied. Required: " + rule.getRequirementSummary();
            } catch (RuleExpressionEvaluator.MissingOperandException e) {
                status = RuleEvaluationStatus.MISSING_EVIDENCE;
                detail = "A value this rule depends on has not been recorded yet: " + e.getMessage();
            } catch (RuntimeException e) {
                status = RuleEvaluationStatus.NOT_APPLICABLE;
                detail = "Rule could not be evaluated: " + e.getMessage();
                log.warn("Rule {} failed to evaluate for transaction {}: {}",
                        rule.getCode(), transaction.getId(), e.toString());
            }
        }

        Set<Fact> touched = context.touchedFacts();
        if (touched.isEmpty()) {
            touched.addAll(candidates);
        }
        evaluation.record(status, actualValue, detail, touched, evaluatedAt);
        syncGeneratedIssue(transaction, rule, evaluation, caller);
        return evaluation;
    }

    private String describeActual(List<Fact> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(Fact::getLabel))
                .map(f -> f.getLabel() + " = " + f.getValue() + (f.getUnit() != null ? " " + f.getUnit() : ""))
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);
    }

    /**
     * Keeps at most one engine-owned issue per rule per transaction: raised when the rule stops
     * passing, resolved automatically once it passes again.
     */
    private void syncGeneratedIssue(Transaction transaction, RegulatoryRule rule,
                                     RuleEvaluation evaluation, User caller) {
        boolean blocking = evaluation.getStatus() == RuleEvaluationStatus.FAILED
                || evaluation.getStatus() == RuleEvaluationStatus.MISSING_EVIDENCE;
        Issue existing = evaluation.getGeneratedIssue();

        if (!blocking) {
            if (existing != null && existing.getStatus() != IssueStatus.RESOLVED
                    && existing.getStatus() != IssueStatus.CLOSED) {
                existing.autoResolve("Automatically resolved: rule " + rule.getCode() + " now passes.");
            }
            return;
        }

        String title = "[" + rule.getCode() + "] " + rule.getTitle();
        String description = evaluation.getDetail();

        if (existing != null) {
            if (existing.getStatus() == IssueStatus.RESOLVED || existing.getStatus() == IssueStatus.CLOSED) {
                existing.reopen(description, rule.getSeverity());
            } else {
                existing.reopen(description, rule.getSeverity());
            }
            existing.getRelatedFacts().clear();
            existing.getRelatedFacts().addAll(evaluation.getSupportingFacts());
            return;
        }

        Workstream workstream = resolveWorkstream(transaction, rule.getWorkstreamType());
        Issue issue = new Issue(workstream, title, description, rule.getSeverity(), null, null, caller);
        issue.setGeneratedByRuleId(rule.getId());
        issue.getRelatedFacts().addAll(evaluation.getSupportingFacts());
        evaluation.setGeneratedIssue(issueRepository.save(issue));
    }

    /**
     * A blocking regulatory finding must land somewhere a team can act on it, so the owning
     * workstream is created on demand when the transaction does not have one yet.
     */
    private Workstream resolveWorkstream(Transaction transaction, WorkstreamType type) {
        return workstreamRepository.findByTransactionId(transaction.getId()).stream()
                .filter(w -> w.getType() == type)
                .findFirst()
                .orElseGet(() -> workstreamRepository.save(
                        new Workstream(transaction, type, "Opened automatically for regulatory findings")));
    }

    private LocalDate estimatedFilingDate(Transaction transaction) {
        return transaction.getEstimatedFilingDate() != null
                ? transaction.getEstimatedFilingDate()
                : LocalDate.now(ZoneOffset.UTC).plusDays(DEFAULT_FILING_HORIZON_DAYS);
    }

    private ReadinessDto buildReadiness(Transaction transaction, List<RuleEvaluation> evaluations) {
        List<RuleEvaluationDto> ruleDtos = evaluations.stream()
                .sorted(Comparator.comparing(e -> e.getRule().getCode()))
                .map(RuleEvaluationDto::from)
                .toList();

        int passed = count(evaluations, RuleEvaluationStatus.PASSED);
        int failed = count(evaluations, RuleEvaluationStatus.FAILED);
        int missing = count(evaluations, RuleEvaluationStatus.MISSING_EVIDENCE);

        ReadinessState state;
        if (evaluations.isEmpty() || failed > 0) {
            state = ReadinessState.NOT_READY;
        } else if (missing > 0) {
            state = ReadinessState.CONDITIONALLY_READY;
        } else {
            state = ReadinessState.READY_FOR_FILING;
        }

        List<BlockingIssueDto> blocking = evaluations.stream()
                .filter(e -> e.getGeneratedIssue() != null)
                .filter(e -> e.getGeneratedIssue().getStatus() != IssueStatus.RESOLVED
                        && e.getGeneratedIssue().getStatus() != IssueStatus.CLOSED)
                .map(e -> BlockingIssueDto.from(e.getGeneratedIssue(), e.getRule().getCode()))
                .toList();

        Instant lastEvaluated = evaluations.stream()
                .map(RuleEvaluation::getEvaluatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new ReadinessDto(state, evaluations.size(), passed, failed, missing,
                transaction.getEstimatedFilingDate(), lastEvaluated, ruleDtos, blocking);
    }

    private int count(List<RuleEvaluation> evaluations, RuleEvaluationStatus status) {
        return (int) evaluations.stream().filter(e -> e.getStatus() == status).count();
    }
}
