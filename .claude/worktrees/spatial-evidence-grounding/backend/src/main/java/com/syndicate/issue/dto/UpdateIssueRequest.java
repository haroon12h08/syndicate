package com.syndicate.issue.dto;

import com.syndicate.issue.IssueSeverity;
import com.syndicate.issue.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateIssueRequest(
        @NotBlank String title,
        String description,
        @NotNull IssueSeverity severity,
        @NotNull IssueStatus status,
        UUID ownerUserId,
        LocalDate dueDate,
        String resolution
) {
}
