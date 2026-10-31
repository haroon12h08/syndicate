package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.Requirement;
import com.syndicate.regulatory.engine.RequirementVersion;

import java.util.List;
import java.util.UUID;

public record RequirementDto(
        UUID id,
        String code,
        String authority,
        String jurisdiction,
        String title,
        String description,
        String transactionType,
        String issueType,
        String severity,
        String status,
        List<RequirementVersionDto> versions
) {
    public static RequirementDto from(Requirement req, List<RequirementVersionDto> versions) {
        return new RequirementDto(
                req.getId(),
                req.getCode(),
                req.getAuthority(),
                req.getJurisdiction(),
                req.getTitle(),
                req.getDescription(),
                req.getTransactionType(),
                req.getIssueType(),
                req.getSeverity(),
                req.getStatus(),
                versions
        );
    }
}
