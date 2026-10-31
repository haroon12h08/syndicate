package com.syndicate.regulatory.engine;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.regulatory.engine.dto.CreateExceptionRequest;
import com.syndicate.regulatory.engine.dto.RegulatoryExceptionDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages regulatory exceptions and approvals (spec §39, invariant 19; plan D5).
 */
@Service
public class RegulatoryExceptionService {

    private final RegulatoryExceptionRepository exceptionRepository;
    private final RegulatoryControlRepository controlRepository;
    private final EvidenceRepository evidenceRepository;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final RegulatoryEngineService engineService;
    private final AuditService auditService;

    public RegulatoryExceptionService(RegulatoryExceptionRepository exceptionRepository,
                                      RegulatoryControlRepository controlRepository,
                                      EvidenceRepository evidenceRepository,
                                      TransactionService transactionService,
                                      PermissionService permissionService,
                                      RegulatoryEngineService engineService,
                                      AuditService auditService) {
        this.exceptionRepository = exceptionRepository;
        this.controlRepository = controlRepository;
        this.evidenceRepository = evidenceRepository;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.engineService = engineService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RegulatoryExceptionDto> listExceptions(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return exceptionRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId).stream()
                .map(RegulatoryExceptionDto::from)
                .toList();
    }

    @Transactional
    public RegulatoryExceptionDto requestException(UUID transactionId, CreateExceptionRequest request, User caller) {
        permissionService.requireTransactionPermission(transactionId, caller.getId(), Permission.REGULATORY_EXCEPTION_REQUEST);
        Transaction transaction = transactionService.findTransaction(transactionId);

        RegulatoryControl control = controlRepository.findById(request.controlId())
                .orElseThrow(() -> new ResourceNotFoundException("Regulatory control not found"));

        Evidence evidence = null;
        if (request.supportingEvidenceId() != null) {
            evidence = evidenceRepository.findById(request.supportingEvidenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supporting evidence document not found"));
        }

        RegulatoryException exception = new RegulatoryException(
                transaction,
                control,
                control.getRequirementVersion(),
                request.reason(),
                request.affectedState(),
                evidence,
                request.riskClassification(),
                caller,
                request.expiryDate(),
                request.mitigationPlan()
        );

        RegulatoryException saved = exceptionRepository.save(exception);

        auditService.record(transactionId, caller, AuditAction.REGULATORY_EXCEPTION_REQUESTED,
                "RegulatoryException", saved.getId(),
                "Requested exception for " + control.getCode() + ": " + request.reason(),
                null, null, null);

        return RegulatoryExceptionDto.from(saved);
    }

    @Transactional
    public RegulatoryExceptionDto approveException(UUID exceptionId, User approver) {
        RegulatoryException exception = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Regulatory exception not found"));

        UUID transactionId = exception.getTransaction().getId();
        permissionService.requireTransactionPermission(transactionId, approver.getId(), Permission.REGULATORY_EXCEPTION_APPROVE);

        // Four-eyes invariant: owner cannot approve their own exception request
        if (exception.getOwner().getId().equals(approver.getId())) {
            throw new ForbiddenException("Four-eyes governance invariant violation: Exception owner ("
                    + approver.getFullName() + ") cannot approve their own exception request.");
        }

        exception.setStatus("APPROVED");
        exception.setApprover(approver);
        exception.setApprovedAt(Instant.now());
        RegulatoryException saved = exceptionRepository.save(exception);

        auditService.record(transactionId, approver, AuditAction.REGULATORY_EXCEPTION_APPROVED,
                "RegulatoryException", saved.getId(),
                "Approved regulatory exception for " + exception.getControl().getCode(),
                null, null, null);

        // Re-evaluate transaction so the control status updates to EXCEPTION
        engineService.evaluateTransaction(transactionId, approver);

        return RegulatoryExceptionDto.from(saved);
    }

    @Transactional
    public RegulatoryExceptionDto rejectException(UUID exceptionId, String rejectionReason, User approver) {
        RegulatoryException exception = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Regulatory exception not found"));

        UUID transactionId = exception.getTransaction().getId();
        permissionService.requireTransactionPermission(transactionId, approver.getId(), Permission.REGULATORY_EXCEPTION_APPROVE);

        exception.setStatus("REJECTED");
        exception.setApprover(approver);
        exception.setApprovedAt(Instant.now());
        RegulatoryException saved = exceptionRepository.save(exception);

        auditService.record(transactionId, approver, AuditAction.REGULATORY_EXCEPTION_REJECTED,
                "RegulatoryException", saved.getId(),
                "Rejected regulatory exception for " + exception.getControl().getCode() + ": " + rejectionReason,
                null, null, null);

        return RegulatoryExceptionDto.from(saved);
    }
}
