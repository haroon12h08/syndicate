package com.syndicate.task.dto;

import com.syndicate.issue.IssueSeverity;
import com.syndicate.transaction.TransactionRole;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        UUID workstreamId,
        IssueSeverity severity,
        TransactionRole requiredRole,
        LocalDate dueDate
) {
}
