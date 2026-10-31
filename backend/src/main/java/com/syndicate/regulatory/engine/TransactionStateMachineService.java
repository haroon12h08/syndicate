package com.syndicate.regulatory.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ConflictException;
import com.syndicate.conflict.ConflictStatus;
import com.syndicate.conflict.FactConflict;
import com.syndicate.conflict.FactConflictRepository;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.regulatory.engine.dto.RegulatoryExceptionDto;
import com.syndicate.regulatory.engine.dto.TransitionBlockerDto;
import com.syndicate.regulatory.engine.dto.TransitionRequest;
import com.syndicate.regulatory.engine.dto.TransitionResultDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.transaction.TransactionService;
import com.syndicate.transaction.TransactionStatus;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic transaction lifecycle state machine with precondition and exception validation (spec §37, §38; plan D6).
 */
@Service
public class TransactionStateMachineService {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final ControlEvaluationRepository evaluationRepository;
    private final RegulatoryExceptionRepository exceptionRepository;
    private final FactConflictRepository conflictRepository;
    private final TransactionLifecycleTransitionRepository transitionRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TransactionStateMachineService(TransactionService transactionService,
                                          TransactionRepository transactionRepository,
                                          ControlEvaluationRepository evaluationRepository,
                                          RegulatoryExceptionRepository exceptionRepository,
                                          FactConflictRepository conflictRepository,
                                          TransactionLifecycleTransitionRepository transitionRepository,
                                          PermissionService permissionService,
                                          AuditService auditService,
                                          ObjectMapper objectMapper) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.evaluationRepository = evaluationRepository;
        this.exceptionRepository = exceptionRepository;
        this.conflictRepository = conflictRepository;
        this.transitionRepository = transitionRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<TransitionBlockerDto> checkTransitionBlockers(UUID transactionId, TransactionLifecycleState targetState, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        Transaction transaction = transactionService.findTransaction(transactionId);
        return evaluateBlockers(transaction, targetState);
    }

    @Transactional
    public TransitionResultDto transition(UUID transactionId, TransitionRequest request, User caller) {
        permissionService.requireTransactionPermission(transactionId, caller.getId(), Permission.TRANSACTION_STATUS_TRANSITION);
        Transaction transaction = transactionService.findTransaction(transactionId);
        TransactionLifecycleState fromState = transaction.getLifecycleState();
        TransactionLifecycleState targetState = request.targetState();

        if (fromState == targetState) {
            throw new BadRequestException("Transaction is already in state " + targetState);
        }

        validateTransitionPath(fromState, targetState);

        List<TransitionBlockerDto> blockers = evaluateBlockers(transaction, targetState);
        List<TransitionBlockerDto> activeUnexemptedBlockers = blockers.stream()
                .filter(b -> !b.exceptionApplied())
                .toList();

        List<RegulatoryExceptionDto> appliedExceptions = exceptionRepository.findByTransactionIdAndStatus(transactionId, "APPROVED")
                .stream().map(RegulatoryExceptionDto::from).toList();

        if (!activeUnexemptedBlockers.isEmpty() && !request.forceWithException()) {
            throw new ConflictException("LIFECYCLE_TRANSITION_BLOCKED",
                    "Cannot advance to " + targetState + ": " + activeUnexemptedBlockers.size() + " blocking prerequisite(s) remain unresolved.");
        }

        // Apply transition
        transaction.setLifecycleState(targetState);
        if (targetState == TransactionLifecycleState.LISTED || targetState == TransactionLifecycleState.CLOSED) {
            transaction.setStatus(TransactionStatus.CLOSED);
        } else if (targetState == TransactionLifecycleState.DRAFT) {
            transaction.setStatus(TransactionStatus.DRAFT);
        } else {
            transaction.setStatus(TransactionStatus.ACTIVE);
        }
        transactionRepository.save(transaction);

        String blockersJson;
        String exceptionsJson;
        try {
            blockersJson = objectMapper.writeValueAsString(blockers);
            exceptionsJson = objectMapper.writeValueAsString(appliedExceptions);
        } catch (Exception e) {
            blockersJson = "[]";
            exceptionsJson = "[]";
        }

        transitionRepository.save(new TransactionLifecycleTransition(
                transaction,
                fromState.name(),
                targetState.name(),
                caller,
                request.reason(),
                blockersJson,
                exceptionsJson
        ));

        auditService.record(transactionId, caller, AuditAction.TRANSACTION_LIFECYCLE_TRANSITIONED,
                "Transaction", transactionId,
                "Transitioned lifecycle state from " + fromState + " to " + targetState,
                null, null, null);

        return new TransitionResultDto(
                true,
                fromState,
                targetState,
                Instant.now(),
                request.reason(),
                blockers,
                appliedExceptions
        );
    }

    private void validateTransitionPath(TransactionLifecycleState from, TransactionLifecycleState to) {
        if (to == TransactionLifecycleState.CLOSED) {
            return; // Closing is allowed from any non-terminal state
        }
        if (from.isTerminal()) {
            throw new BadRequestException("Cannot transition from terminal state " + from);
        }
    }

    private List<TransitionBlockerDto> evaluateBlockers(Transaction transaction, TransactionLifecycleState targetState) {
        List<TransitionBlockerDto> blockers = new ArrayList<>();
        UUID transactionId = transaction.getId();

        // Target states that require regulatory gating: INTERNAL_APPROVAL, FILING_READY, FILED
        boolean requiresRegulatoryGating = targetState == TransactionLifecycleState.INTERNAL_APPROVAL
                || targetState == TransactionLifecycleState.FILING_READY
                || targetState == TransactionLifecycleState.FILED;

        if (requiresRegulatoryGating) {
            List<ControlEvaluation> evaluations = evaluationRepository.findByTransactionId(transactionId);
            Map<UUID, RegulatoryException> approvedExceptions = exceptionRepository
                    .findByTransactionIdAndStatus(transactionId, "APPROVED").stream()
                    .filter(RegulatoryException::isCurrentlyEffective)
                    .collect(Collectors.toMap(e -> e.getControl().getId(), e -> e, (e1, e2) -> e1));

            for (ControlEvaluation eval : evaluations) {
                if ("FAIL".equalsIgnoreCase(eval.getStatus()) || "MISSING".equalsIgnoreCase(eval.getStatus())) {
                    boolean hasException = approvedExceptions.containsKey(eval.getControl().getId());
                    blockers.add(new TransitionBlockerDto(
                            eval.getControl().getCode(),
                            eval.getControl().getName(),
                            eval.getExplanation(),
                            eval.getControl().getSeverity(),
                            "CONTROL",
                            eval.getControl().getId(),
                            true,
                            hasException
                    ));
                }
            }

            // Check unresolved critical FactConflicts
            List<FactConflict> openConflicts = conflictRepository.findByTransactionIdAndStatus(transactionId, ConflictStatus.OPEN);
            for (FactConflict fc : openConflicts) {
                blockers.add(new TransitionBlockerDto(
                        "CONFLICT_UNRESOLVED",
                        "Open Fact Conflict on " + fc.getFactKey(),
                        "Zero AI Authority: material conflict between verified fact and source candidate must be resolved by human review.",
                        "CRITICAL",
                        "CONFLICT",
                        fc.getId(),
                        false,
                        false
                ));
            }
        }

        return blockers;
    }
}
