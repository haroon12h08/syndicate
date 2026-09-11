package com.syndicate.issue.dto;

import com.syndicate.issue.IssueSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateIssueRequest(
        @NotBlank String title,
        String description,
        @NotNull IssueSeverity severity,
        UUID ownerUserId,
        LocalDate dueDate,
        List<UUID> relatedFactIds,
        List<UUID> relatedEvidenceIds
) {
}
