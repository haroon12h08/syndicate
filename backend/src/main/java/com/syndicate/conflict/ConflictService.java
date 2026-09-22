package com.syndicate.conflict;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ConflictException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.conflict.dto.ConflictDto;
import com.syndicate.conflict.dto.ResolveConflictRequest;
import com.syndicate.drhp.ChangePropagationService;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.Fact;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Human adjudication of conflicts (spec §11, workflow D). The system never resolves one itself. */
@Service
@Transactional
public class ConflictService {

    private final FactConflictRepository conflictRepository;
    private final ConflictResolutionRepository resolutionRepository;
    private final EvidenceRepository evidenceRepository;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final ChangePropagationService changePropagationService;
    private final ConflictDetectionService detectionService;
    private final AuditService auditService;

    public ConflictService(FactConflictRepository conflictRepository, ConflictResolutionRepository resolutionRepository,
                           EvidenceRepository evidenceRepository, TransactionService transactionService,
                           PermissionService permissionService, ChangePropagationService changePropagationService,
                           ConflictDetectionService detectionService, AuditService auditService) {
        this.conflictRepository = conflictRepository;
        this.resolutionRepository = resolutionRepository;
        this.evidenceRepository = evidenceRepository;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.changePropagationService = changePropagationService;
        this.detectionService = detectionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ConflictDto> list(UUID transactionId, ConflictStatus status, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        List<FactConflict> conflicts = status == null
                ? conflictRepository.findByTransactionIdOrderByDetectedAtDesc(transactionId)
                : conflictRepository.findByTransactionIdAndStatus(transactionId, status);
        return conflicts.stream().map(ConflictDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ConflictDto get(UUID conflictId, UUID callerId) {
        FactConflict conflict = find(conflictId);
        transactionService.requireMembership(conflict.getTransactionId(), callerId);
        return ConflictDto.from(conflict);
    }

    /**
     * Records which value stands and why. The other competing facts are REJECTED (kept, never
     * deleted), and everything that depended on them is marked stale.
     */
    public ConflictDto resolve(UUID conflictId, ResolveConflictRequest request, User caller) {
        FactConflict conflict = find(conflictId);
        UUID transactionId = conflict.getTransactionId();
        transactionService.requireMembership(transactionId, caller.getId());
        permissionService.requireTransactionPermission(transactionId, caller.getId(), Permission.CONFLICT_RESOLVE);
        if (conflict.getStatus() != ConflictStatus.OPEN) {
            throw new ConflictException("CONFLICT_NOT_OPEN", "This conflict is already " + conflict.getStatus());
        }
        Fact chosen = conflict.getMembers().stream()
                .filter(f -> f.getId().equals(request.chosenFactId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("The chosen fact is not one of the competing facts"));

        Set<Evidence> considered = new HashSet<>();
        if (request.evidenceConsideredIds() != null) {
            for (UUID evidenceId : request.evidenceConsideredIds()) {
                Evidence evidence = evidenceRepository.findById(evidenceId)
                        .filter(e -> e.getWorkstream().getTransaction().getId().equals(transactionId))
                        .orElseThrow(() -> new BadRequestException("Unknown evidence " + evidenceId));
                considered.add(evidence);
            }
        }

        Instant now = Instant.now();
        Set<Fact> rejected = conflict.getMembers().stream()
                .filter(f -> !f.getId().equals(chosen.getId()))
                .collect(Collectors.toSet());
        rejected.forEach(Fact::markRejected);
        ConflictResolution resolution = resolutionRepository.save(new ConflictResolution(conflict, chosen, rejected,
                considered, request.reason(), caller, now));
        conflict.getResolutions().add(resolution);
        conflict.markResolved(now);
        if (conflict.getIssue() != null) {
            conflict.getIssue().autoResolve("Resolved by " + caller.getFullName() + ": kept " + chosen.getValue()
                    + ". " + request.reason());
        }
        auditService.record(transactionId, caller, AuditAction.CONFLICT_RESOLVED, "FactConflict", conflict.getId(),
                "Kept " + chosen.getLabel() + " = " + chosen.getValue() + "; rejected " + rejected.size() + " value(s)",
                rejected.stream().map(Fact::getValue).sorted().collect(Collectors.joining(" | ")), chosen.getValue(),
                request.reason());

        for (Fact fact : rejected) {
            changePropagationService.propagateFactChange(transactionId, fact.getLineageId(),
                    "Source fact \"" + fact.getLabel() + "\" (" + fact.getValue() + ") was rejected in a conflict resolution.");
        }
        detectionService.detect(transactionId, caller);
        return ConflictDto.from(conflict);
    }

    private FactConflict find(UUID conflictId) {
        return conflictRepository.findById(conflictId)
                .orElseThrow(() -> new ResourceNotFoundException("Conflict not found: " + conflictId));
    }
}
