package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.RequirementVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RequirementVersionDto(
        UUID id,
        UUID requirementId,
        String requirementCode,
        int versionNumber,
        String citation,
        String sourceUrl,
        String sourceDocumentHash,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String applicabilityExpression,
        String requiredFacts,
        String requiredEvidenceTypes,
        String requiredReviews,
        String requiredApprovals,
        String remediationGuide,
        String ruleExpression,
        String status,
        UUID authorId,
        String authorName,
        UUID approvedById,
        String approvedByName,
        Instant approvedAt,
        boolean isUnsourced,
        List<RequirementTestCaseDto> testCases
) {
    public static RequirementVersionDto from(RequirementVersion v, List<RequirementTestCaseDto> testCases) {
        return new RequirementVersionDto(
                v.getId(),
                v.getRequirement().getId(),
                v.getRequirement().getCode(),
                v.getVersionNumber(),
                v.getCitation(),
                v.getSourceUrl(),
                v.getSourceDocumentHash(),
                v.getEffectiveFrom(),
                v.getEffectiveTo(),
                v.getApplicabilityExpression(),
                v.getRequiredFacts(),
                v.getRequiredEvidenceTypes(),
                v.getRequiredReviews(),
                v.getRequiredApprovals(),
                v.getRemediationGuide(),
                v.getRuleExpression(),
                v.getStatus(),
                v.getAuthor() != null ? v.getAuthor().getId() : null,
                v.getAuthor() != null ? v.getAuthor().getFullName() : null,
                v.getApprovedBy() != null ? v.getApprovedBy().getId() : null,
                v.getApprovedBy() != null ? v.getApprovedBy().getFullName() : null,
                v.getApprovedAt(),
                v.isUnsourced(),
                testCases
        );
    }
}
