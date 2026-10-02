package com.syndicate.workbench;

import com.syndicate.conflict.ConflictStatus;
import com.syndicate.conflict.FactConflict;
import com.syndicate.conflict.FactConflictRepository;
import com.syndicate.drhp.Disclosure;
import com.syndicate.drhp.DisclosureRepository;
import com.syndicate.drhp.DisclosureStatus;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.drhp.DrhpStatus;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceQuality;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.regulatory.RuleEvaluation;
import com.syndicate.regulatory.RuleEvaluationRepository;
import com.syndicate.regulatory.RuleEvaluationStatus;
import com.syndicate.review.ReviewService;
import com.syndicate.review.dto.MissingReviewDto;
import com.syndicate.transaction.TransactionService;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Answers "what prevents this transaction from being confidently submitted right now?" (spec §8)
 * by collecting every concrete blocker and deriving readiness from them (spec §14). Pure reads:
 * the same state always yields the same answer.
 */
@Service
public class WorkbenchService {

    private static final Set<FactStatus> NOT_CURRENT = EnumSet.of(FactStatus.SUPERSEDED, FactStatus.REJECTED);

    private final TransactionService transactionService;
    private final FactRepository factRepository;
    private final FactConflictRepository conflictRepository;
    private final RuleEvaluationRepository ruleEvaluationRepository;
    private final DisclosureRepository disclosureRepository;
    private final DrhpDocumentRepository drhpRepository;
    private final ReviewService reviewService;

    public WorkbenchService(TransactionService transactionService, FactRepository factRepository,
                            FactConflictRepository conflictRepository, RuleEvaluationRepository ruleEvaluationRepository,
                            DisclosureRepository disclosureRepository, DrhpDocumentRepository drhpRepository,
                            ReviewService reviewService) {
        this.transactionService = transactionService;
        this.factRepository = factRepository;
        this.conflictRepository = conflictRepository;
        this.ruleEvaluationRepository = ruleEvaluationRepository;
        this.disclosureRepository = disclosureRepository;
        this.drhpRepository = drhpRepository;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public WorkbenchDto workbench(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        List<BlockerDto> blockers = new ArrayList<>();

        List<FactConflict> openConflicts = conflictRepository.findByTransactionIdAndStatus(transactionId,
                ConflictStatus.OPEN);
        Set<UUID> conflicted = new HashSet<>();
        for (FactConflict conflict : openConflicts) {
            boolean material = conflict.getMembers().stream()
                    .anyMatch(f -> f.getMateriality().requiresIndependentVerification());
            conflict.getMembers().forEach(f -> conflicted.add(f.getId()));
            String label = conflict.getMembers().stream().map(Fact::getLabel).findFirst().orElse(conflict.getFactKey());
            blockers.add(new BlockerDto("OPEN_CONFLICT", material ? BlockerSeverity.BLOCKING : BlockerSeverity.CONDITIONAL,
                    "CONFLICT", conflict.getId(), "Conflicting values for " + label,
                    conflict.getMembers().size() + " sources disagree over the same period",
                    "Choose the value that stands and record why", "LEAD_BANKER"));
        }

        List<Fact> current = factRepository.findByWorkstreamTransactionId(transactionId).stream()
                .filter(f -> !NOT_CURRENT.contains(f.getStatus()))
                .toList();
        int material = 0;
        int verified = 0;
        int withEvidence = 0;
        int withAcceptable = 0;
        int inConflict = 0;
        for (Fact fact : current) {
            if (!fact.getMateriality().requiresIndependentVerification()) {
                continue;
            }
            material++;
            boolean hasEvidence = !fact.getEvidence().isEmpty();
            boolean hasAcceptable = fact.getEvidence().stream().anyMatch(e -> e.getQuality() == EvidenceQuality.ACCEPTABLE);
            boolean onlyUnsupportive = hasEvidence && fact.getEvidence().stream()
                    .map(Evidence::getQuality).allMatch(EvidenceQuality.UNSUPPORTIVE::contains);
            if (fact.getStatus() == FactStatus.VERIFIED) {
                verified++;
            }
            if (hasEvidence) {
                withEvidence++;
            }
            if (hasAcceptable) {
                withAcceptable++;
            }
            if (conflicted.contains(fact.getId())) {
                inConflict++;
            }
            String title = fact.getLabel() + " = " + fact.getValue() + (fact.getUnit() != null ? " " + fact.getUnit() : "");
            if (!hasEvidence) {
                blockers.add(new BlockerDto("MATERIAL_FACT_WITHOUT_EVIDENCE", BlockerSeverity.BLOCKING, "FACT",
                        fact.getId(), title, fact.getMateriality() + " fact with no supporting document",
                        "Link the document that establishes this value", null));
            } else if (onlyUnsupportive) {
                blockers.add(new BlockerDto("MATERIAL_FACT_INSUFFICIENT_EVIDENCE", BlockerSeverity.BLOCKING, "FACT",
                        fact.getId(), title, "Every supporting document was assessed insufficient or invalid",
                        "Obtain and link adequate evidence", null));
            }
            if (fact.getStatus() != FactStatus.VERIFIED && !conflicted.contains(fact.getId())) {
                // financial facts are verified by a restricted set of roles; say which
                String owner = fact.getWorkstream().getType() == WorkstreamType.FINANCIAL_DUE_DILIGENCE
                        ? PermissionService.rolesWith(Permission.FACT_VERIFY_FINANCIAL).stream()
                                .map(Enum::name).findFirst().orElse(null)
                        : null;
                blockers.add(new BlockerDto("MATERIAL_FACT_UNVERIFIED", BlockerSeverity.BLOCKING, "FACT", fact.getId(),
                        title, fact.getMateriality() + " fact not yet verified",
                        "Verification by someone other than the person who recorded it", owner));
            }
        }

        if (current.isEmpty()) {
            blockers.add(new BlockerDto("NOTHING_RECORDED", BlockerSeverity.CONDITIONAL, "TRANSACTION",
                    transactionId, "Nothing has been recorded yet",
                    "This transaction holds no facts about the company",
                    "Upload the company's documents, then confirm what they establish", null));
        }

        List<RuleEvaluation> evaluations = ruleEvaluationRepository.findByTransactionId(transactionId);
        if (evaluations.isEmpty() && !current.isEmpty()) {
            blockers.add(new BlockerDto("RULES_NOT_EVALUATED", BlockerSeverity.CONDITIONAL, "TRANSACTION",
                    transactionId, "Configured rules have not been evaluated",
                    "No rule evaluation has been run for this transaction", "Run readiness evaluation", null));
        }
        for (RuleEvaluation evaluation : evaluations) {
            if (evaluation.getStatus() == RuleEvaluationStatus.FAILED) {
                blockers.add(new BlockerDto("RULE_FAILED", BlockerSeverity.BLOCKING, "RULE_EVALUATION",
                        evaluation.getId(), "[" + evaluation.getRule().getCode() + "] " + evaluation.getRule().getTitle(),
                        "Based on the configured rule and current verified facts, this evaluates to FAIL. "
                                + evaluation.getDetail(), "Correct the underlying facts or record an exception", null));
            } else if (evaluation.getStatus() == RuleEvaluationStatus.MISSING_EVIDENCE) {
                blockers.add(new BlockerDto("RULE_MISSING_INPUT", BlockerSeverity.CONDITIONAL, "RULE_EVALUATION",
                        evaluation.getId(), "[" + evaluation.getRule().getCode() + "] " + evaluation.getRule().getTitle(),
                        evaluation.getDetail(), "Record and verify the facts this rule needs", null));
            }
        }

        List<Disclosure> disclosures = disclosureRepository.findByTransactionIdOrderByOrderIndexAscCreatedAtAsc(transactionId);
        int stale = 0;
        for (Disclosure disclosure : disclosures) {
            if (disclosure.getStatus() == DisclosureStatus.STALE_REQUIRING_REVIEW) {
                stale++;
                blockers.add(new BlockerDto("STALE_DISCLOSURE", BlockerSeverity.STALE, "DISCLOSURE", disclosure.getId(),
                        disclosure.getTitle(), disclosure.getStaleReason(), "Re-read against current facts and re-approve",
                        null));
            }
        }
        drhpRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId)
                .filter(doc -> doc.getStatus() == DrhpStatus.INVALIDATED)
                .ifPresent(doc -> blockers.add(new BlockerDto("DRHP_INVALIDATED", BlockerSeverity.STALE, "DOCUMENT",
                        doc.getId(), "DRHP v" + doc.getVersion() + " no longer matches the record",
                        doc.getInvalidatedReason(), "Recompile after the change is reviewed", null)));

        for (MissingReviewDto missing : reviewService.missingReviews(transactionId)) {
            blockers.add(new BlockerDto("REVIEW_MISSING", BlockerSeverity.AWAITING_REVIEW, "FACT", missing.factId(),
                    missing.label(), missing.materiality() + " fact lacks a current review from "
                    + missing.requiredRole(), "Review the current version", missing.requiredRole().name()));
        }

        blockers.sort(Comparator.comparing(BlockerDto::severity).thenComparing(BlockerDto::type));
        Map<BlockerSeverity, Long> counts = new EnumMap<>(BlockerSeverity.class);
        for (BlockerSeverity severity : BlockerSeverity.values()) {
            counts.put(severity, blockers.stream().filter(b -> b.severity() == severity).count());
        }
        DerivedReadiness readiness = derive(counts);
        return new WorkbenchDto(readiness, explain(readiness, counts), counts, blockers,
                new CoverageDto(material, verified, withEvidence, withAcceptable, inConflict, stale, disclosures.size()),
                Instant.now());
    }

    static DerivedReadiness derive(Map<BlockerSeverity, Long> counts) {
        if (counts.get(BlockerSeverity.BLOCKING) > 0) {
            return DerivedReadiness.NOT_READY;
        }
        if (counts.get(BlockerSeverity.STALE) > 0) {
            return DerivedReadiness.STALE_AFTER_CHANGE;
        }
        if (counts.get(BlockerSeverity.CONDITIONAL) > 0) {
            return DerivedReadiness.CONDITIONALLY_READY;
        }
        if (counts.get(BlockerSeverity.AWAITING_REVIEW) > 0) {
            return DerivedReadiness.READY_FOR_REVIEW;
        }
        return DerivedReadiness.READY_FOR_FILING;
    }

    private static String explain(DerivedReadiness readiness, Map<BlockerSeverity, Long> counts) {
        return switch (readiness) {
            case NOT_READY -> counts.get(BlockerSeverity.BLOCKING) + " blocking item(s) must be cleared.";
            case STALE_AFTER_CHANGE -> counts.get(BlockerSeverity.STALE)
                    + " output(s) were built from facts that have since changed.";
            case CONDITIONALLY_READY -> counts.get(BlockerSeverity.CONDITIONAL) + " known gap(s) remain.";
            case READY_FOR_REVIEW -> counts.get(BlockerSeverity.AWAITING_REVIEW) + " required review(s) outstanding.";
            case READY_FOR_FILING -> "No blockers under the configured rules. This is not a regulatory approval.";
        };
    }
}
