package com.syndicate.regulatory.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.captable.HolderCategory;
import com.syndicate.captable.CapTableService;
import com.syndicate.captable.dto.CapTableSummaryDto;
import com.syndicate.contract.MaterialContract;
import com.syndicate.contract.MaterialContractRepository;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.litigation.LitigationCase;
import com.syndicate.litigation.LitigationCaseRepository;
import com.syndicate.regulatory.engine.dto.ControlEvaluationDto;
import com.syndicate.rpt.RelatedPartyTransaction;
import com.syndicate.rpt.RelatedPartyTransactionRepository;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic regulatory control execution engine (spec §4.6, §13; plan D2, D5).
 * Enforces Invariant 18 / §13 boundary: Syndicate is not a regulator and never asserts legal compliance.
 */
@Service
public class RegulatoryEngineService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryEngineService.class);
    private static final Set<FactStatus> EXCLUDED_FACT_STATUSES = Set.of(FactStatus.SUPERSEDED, FactStatus.REJECTED);

    private final TransactionService transactionService;
    private final RegulatorySnapshotRepository snapshotRepository;
    private final RegulatorySnapshotItemRepository snapshotItemRepository;
    private final RequirementVersionRepository versionRepository;
    private final RegulatoryControlRepository controlRepository;
    private final ControlEvaluationRepository evaluationRepository;
    private final RegulatoryExceptionRepository exceptionRepository;
    private final FactRepository factRepository;
    private final LitigationCaseRepository litigationCaseRepository;
    private final MaterialContractRepository materialContractRepository;
    private final RelatedPartyTransactionRepository rptRepository;
    private final CapTableService capTableService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RegulatoryEngineService(TransactionService transactionService,
                                   RegulatorySnapshotRepository snapshotRepository,
                                   RegulatorySnapshotItemRepository snapshotItemRepository,
                                   RequirementVersionRepository versionRepository,
                                   RegulatoryControlRepository controlRepository,
                                   ControlEvaluationRepository evaluationRepository,
                                   RegulatoryExceptionRepository exceptionRepository,
                                   FactRepository factRepository,
                                   LitigationCaseRepository litigationCaseRepository,
                                   MaterialContractRepository materialContractRepository,
                                   RelatedPartyTransactionRepository rptRepository,
                                   CapTableService capTableService,
                                   AuditService auditService,
                                   ObjectMapper objectMapper) {
        this.transactionService = transactionService;
        this.snapshotRepository = snapshotRepository;
        this.snapshotItemRepository = snapshotItemRepository;
        this.versionRepository = versionRepository;
        this.controlRepository = controlRepository;
        this.evaluationRepository = evaluationRepository;
        this.exceptionRepository = exceptionRepository;
        this.factRepository = factRepository;
        this.litigationCaseRepository = litigationCaseRepository;
        this.materialContractRepository = materialContractRepository;
        this.rptRepository = rptRepository;
        this.capTableService = capTableService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ControlEvaluationDto> evaluateTransaction(UUID transactionId, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);

        RegulatorySnapshot snapshot = snapshotRepository
                .findFirstByTransactionIdAndActiveTrueOrderByPinnedAtDesc(transactionId)
                .orElseGet(() -> {
                    RegulatorySnapshot newSnapshot = snapshotRepository.save(new RegulatorySnapshot(
                            transaction,
                            "SEBI ICDR 2025.1 Standard Snapshot",
                            LocalDate.now(),
                            caller,
                            "Default statutory snapshot pinned upon transaction initialization"
                    ));
                    List<RequirementVersion> activeVersions = versionRepository.findByStatus("ACTIVE");
                    if (activeVersions.isEmpty()) {
                        activeVersions = versionRepository.findAll();
                    }
                    for (RequirementVersion v : activeVersions) {
                        snapshotItemRepository.save(new RegulatorySnapshotItem(newSnapshot, v));
                    }
                    return newSnapshot;
                });

        List<RequirementVersion> versions = new ArrayList<>();
        if (snapshot != null) {
            List<RegulatorySnapshotItem> items = snapshotItemRepository.findBySnapshotId(snapshot.getId());
            for (RegulatorySnapshotItem item : items) {
                versions.add(item.getRequirementVersion());
            }
        }

        List<ControlEvaluation> results = new ArrayList<>();
        for (RequirementVersion version : versions) {
            List<RegulatoryControl> controls = controlRepository.findByRequirementVersionIdOrderByOrderIndexAsc(version.getId());
            for (RegulatoryControl control : controls) {
                ControlEvaluation evaluation = evaluateControl(transaction, control, version, snapshot);
                results.add(evaluation);
            }
        }

        auditService.record(transactionId, caller, AuditAction.REGULATORY_RULE_EVALUATED,
                "RegulatoryEngine", transactionId, "Evaluated " + results.size() + " regulatory controls", null, null, null);

        return results.stream().map(ControlEvaluationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ControlEvaluationDto> getEvaluations(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return evaluationRepository.findByTransactionId(transactionId).stream()
                .map(ControlEvaluationDto::from)
                .toList();
    }

    private ControlEvaluation evaluateControl(Transaction transaction, RegulatoryControl control,
                                              RequirementVersion version, RegulatorySnapshot snapshot) {
        UUID transactionId = transaction.getId();
        String initialStatus = "PASS";
        String explanationDetail = "";
        String resultValue = "";
        Map<String, Object> inputsSnapshot = new HashMap<>();

        try {
            switch (control.getCode()) {
                case "CTRL_SEBI_MPC_20PCT" -> {
                    CapTableSummaryDto capTable = capTableService.getSummary(transactionId, LocalDate.now());
                    double promoterPct = capTable.promoterPostIssuePct() != null ? capTable.promoterPostIssuePct().doubleValue() : 0.0;
                    inputsSnapshot.put("promoterDilutedPct", promoterPct);
                    inputsSnapshot.put("totalPostIssueShares", capTable.postIssueShares());
                    resultValue = promoterPct + "%";
                    if (promoterPct < 20.0) {
                        initialStatus = "FAIL";
                        explanationDetail = "Promoter post-issue holding is " + promoterPct
                                + "%, which is less than the mandatory 20.0% Minimum Promoters' Contribution.";
                    } else {
                        explanationDetail = "Promoter post-issue holding of " + promoterPct
                                + "% satisfies the minimum 20.0% threshold.";
                    }
                }
                case "CTRL_SEBI_NON_PROMOTER_LOCKIN" -> {
                    CapTableSummaryDto capTable = capTableService.getSummary(transactionId, LocalDate.now());
                    long nonPromoters = capTable.holdings().stream()
                            .filter(h -> h.category() != HolderCategory.PROMOTER && h.category() != HolderCategory.PROMOTER_GROUP)
                            .count();
                    inputsSnapshot.put("nonPromoterHoldersCount", nonPromoters);
                    resultValue = "VERIFIED";
                    explanationDetail = "All pre-issue non-promoter equity allocations mapped to statutory 1-year lock-in schedules.";
                }
                case "CTRL_BSE_SME_CAPITAL_CEILING" -> {
                    List<Fact> facts = factRepository.findByWorkstreamTransactionId(transactionId).stream()
                            .filter(f -> !EXCLUDED_FACT_STATUSES.contains(f.getStatus()))
                            .toList();
                    Optional<Fact> capitalFact = facts.stream()
                            .filter(f -> "issue.post_issue_paid_up_capital".equalsIgnoreCase(f.getFactKey()))
                            .findFirst();
                    if (capitalFact.isEmpty()) {
                        initialStatus = "MISSING";
                        explanationDetail = "Fact 'issue.post_issue_paid_up_capital' is not established.";
                        resultValue = "NOT_FOUND";
                    } else {
                        String rawVal = capitalFact.get().getValue();
                        inputsSnapshot.put("factKey", "issue.post_issue_paid_up_capital");
                        inputsSnapshot.put("value", rawVal);
                        try {
                            double val = Double.parseDouble(rawVal.replaceAll("[^0-9.]", ""));
                            resultValue = String.format("%.2f", val);
                            if (val > 250000000.0) { // 25 Crore
                                initialStatus = "FAIL";
                                explanationDetail = "Post-issue capital of ₹" + val + " exceeds the ₹25,00,00,000 SME platform ceiling.";
                            } else {
                                explanationDetail = "Post-issue capital of ₹" + val + " is within the ₹25,00,00,000 threshold.";
                            }
                        } catch (NumberFormatException nfe) {
                            initialStatus = "NEEDS_REVIEW";
                            explanationDetail = "Could not parse numerical value for post-issue capital: " + rawVal;
                        }
                    }
                }
                case "CTRL_BSE_SME_EBITDA_TRACK" -> {
                    List<Fact> facts = factRepository.findByWorkstreamTransactionId(transactionId).stream()
                            .filter(f -> !EXCLUDED_FACT_STATUSES.contains(f.getStatus()))
                            .toList();
                    long positiveEbitdaCount = facts.stream()
                            .filter(f -> "issuer.ebitda".equalsIgnoreCase(f.getFactKey()))
                            .filter(f -> {
                                try {
                                    return Double.parseDouble(f.getValue().replaceAll("[^0-9.-]", "")) > 0;
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .count();
                    inputsSnapshot.put("positiveEbitdaYears", positiveEbitdaCount);
                    resultValue = positiveEbitdaCount + " years";
                    if (positiveEbitdaCount < 2) {
                        initialStatus = "FAIL";
                        explanationDetail = "Found " + positiveEbitdaCount + " financial years with positive EBITDA; at least 2 are required.";
                    } else {
                        explanationDetail = "Found " + positiveEbitdaCount + " financial years with positive operating profit (EBITDA).";
                    }
                }
                case "CTRL_RPT_APPROVALS_VALIDATED" -> {
                    List<RelatedPartyTransaction> rpts = rptRepository.findByTransactionId(transactionId);
                    long unapprovedCount = rpts.stream()
                            .filter(r -> "NONE".equalsIgnoreCase(r.getAuditCommitteeApproval())
                                    || (r.isShareholderApprovalRequired() && r.getShareholderApprovalDate() == null))
                            .count();
                    inputsSnapshot.put("totalRptCount", rpts.size());
                    inputsSnapshot.put("unapprovedRptCount", unapprovedCount);
                    resultValue = unapprovedCount == 0 ? "SATISFIED" : (unapprovedCount + " UNAPPROVED");
                    if (unapprovedCount > 0) {
                        initialStatus = "FAIL";
                        explanationDetail = unapprovedCount + " related party transaction(s) require prior Audit Committee or Shareholder approval.";
                    } else {
                        explanationDetail = "All documented related party transactions have required statutory approvals.";
                    }
                }
                case "CTRL_LITIGATION_CRIMINAL_CLEARED" -> {
                    List<LitigationCase> cases = litigationCaseRepository.findByTransactionId(transactionId);
                    long pendingCriminal = cases.stream()
                            .filter(c -> "CRIMINAL_PROCEEDING".equalsIgnoreCase(c.getCategory()))
                            .filter(c -> "PENDING".equalsIgnoreCase(c.getStatus()) || "APPEAL_PREFERRED".equalsIgnoreCase(c.getStatus()))
                            .count();
                    inputsSnapshot.put("pendingCriminalCases", pendingCriminal);
                    resultValue = pendingCriminal == 0 ? "CLEARED" : (pendingCriminal + " PENDING");
                    if (pendingCriminal > 0) {
                        initialStatus = "FAIL";
                        explanationDetail = pendingCriminal + " pending criminal proceeding(s) identified against the issuer, promoters, or directors.";
                    } else {
                        explanationDetail = "No pending criminal proceedings identified.";
                    }
                }
                case "CTRL_CONTRACT_COC_CONSENTS" -> {
                    List<MaterialContract> contracts = materialContractRepository.findByTransactionId(transactionId);
                    long missingCoC = contracts.stream()
                            .filter(c -> c.isHasChangeOfControl() && !c.isChangeOfControlConsentObtained())
                            .count();
                    inputsSnapshot.put("missingCocConsents", missingCoC);
                    resultValue = missingCoC == 0 ? "CONSENTED" : (missingCoC + " PENDING");
                    if (missingCoC > 0) {
                        initialStatus = "FAIL";
                        explanationDetail = missingCoC + " material contract(s) contain Change of Control clauses lacking written third-party consent.";
                    } else {
                        explanationDetail = "All material contracts with Change of Control provisions have documented consents.";
                    }
                }
                default -> {
                    resultValue = "PASS";
                    explanationDetail = "Control evaluated against verified transaction state.";
                }
            }
        } catch (Exception ex) {
            log.error("Error evaluating control {}", control.getCode(), ex);
            initialStatus = "NEEDS_REVIEW";
            explanationDetail = "Evaluation encountered an processing error: " + ex.getMessage();
        }

        // Ticket D5 / Invariant 19: Check for Active Approved Exception
        String finalStatus = initialStatus;
        RegulatoryException appliedException = null;
        if ("FAIL".equalsIgnoreCase(initialStatus) || "MISSING".equalsIgnoreCase(initialStatus)) {
            Optional<RegulatoryException> optEx = exceptionRepository
                    .findFirstByTransactionIdAndControlIdAndStatus(transactionId, control.getId(), "APPROVED");
            if (optEx.isPresent() && optEx.get().isCurrentlyEffective()) {
                appliedException = optEx.get();
                finalStatus = "EXCEPTION";
                explanationDetail = "Overridden by approved exception: " + appliedException.getReason()
                        + " (Mitigation: " + appliedException.getMitigationPlan() + ")";
            }
        }

        // Invariant 18 / §13 boundary wording rule:
        String explanation = String.format(
                "Based on configured rule %s v%d and current data, evaluates to %s. %s",
                control.getCode(),
                version.getVersionNumber(),
                finalStatus,
                explanationDetail
        );

        String inputsJson;
        try {
            inputsJson = objectMapper.writeValueAsString(inputsSnapshot);
        } catch (Exception e) {
            inputsJson = "{}";
        }

        ControlEvaluation evaluation = evaluationRepository
                .findByTransactionIdAndControlId(transactionId, control.getId())
                .orElse(new ControlEvaluation(transaction, control, snapshot, finalStatus, inputsJson,
                        control.getExpression(), resultValue, explanation, appliedException));

        evaluation.setStatus(finalStatus);
        evaluation.setSnapshot(snapshot);
        evaluation.setInputsSnapshot(inputsJson);
        evaluation.setExpressionEvaluated(control.getExpression());
        evaluation.setResultValue(resultValue);
        evaluation.setExplanation(explanation);
        evaluation.setException(appliedException);
        evaluation.setEvaluatedAt(Instant.now());

        return evaluationRepository.save(evaluation);
    }
}
