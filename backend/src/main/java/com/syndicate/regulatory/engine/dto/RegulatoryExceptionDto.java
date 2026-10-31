package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.RegulatoryException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RegulatoryExceptionDto(
        UUID id,
        UUID transactionId,
        UUID controlId,
        String controlCode,
        String controlName,
        String requirementCode,
        String reason,
        String affectedState,
        UUID supportingEvidenceId,
        String supportingEvidenceFilename,
        String riskClassification,
        UUID ownerId,
        String ownerName,
        UUID approverId,
        String approverName,
        Instant approvedAt,
        String status,
        LocalDate expiryDate,
        String mitigationPlan,
        boolean isEffective,
        Instant createdAt
) {
    public static RegulatoryExceptionDto from(RegulatoryException ex) {
        return new RegulatoryExceptionDto(
                ex.getId(),
                ex.getTransaction().getId(),
                ex.getControl().getId(),
                ex.getControl().getCode(),
                ex.getControl().getName(),
                ex.getRequirementVersion().getRequirement().getCode(),
                ex.getReason(),
                ex.getAffectedState(),
                ex.getSupportingEvidence() != null ? ex.getSupportingEvidence().getId() : null,
                ex.getSupportingEvidence() != null ? ex.getSupportingEvidence().getFileName() : null,
                ex.getRiskClassification(),
                ex.getOwner().getId(),
                ex.getOwner().getFullName(),
                ex.getApprover() != null ? ex.getApprover().getId() : null,
                ex.getApprover() != null ? ex.getApprover().getFullName() : null,
                ex.getApprovedAt(),
                ex.getStatus(),
                ex.getExpiryDate(),
                ex.getMitigationPlan(),
                ex.isCurrentlyEffective(),
                ex.getCreatedAt()
        );
    }
}
