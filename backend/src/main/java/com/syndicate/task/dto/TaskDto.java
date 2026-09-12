package com.syndicate.task.dto;

import com.syndicate.issue.IssueSeverity;
import com.syndicate.task.TaskStatus;
import com.syndicate.task.TransactionTask;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.UserDto;
import com.syndicate.workstream.WorkstreamType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskDto(
        UUID id,
        UUID transactionId,
        UUID workstreamId,
        WorkstreamType workstreamType,
        String title,
        String description,
        TaskStatus status,
        IssueSeverity severity,
        TransactionRole requiredRole,
        UserDto assignedUser,
        boolean escalatedToLeads,
        String resolutionNote,
        LocalDate dueDate,
        UUID sourceRuleId,
        UUID sourceIssueId,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskDto from(TransactionTask task) {
        return new TaskDto(
                task.getId(),
                task.getTransaction().getId(),
                task.getWorkstream() != null ? task.getWorkstream().getId() : null,
                task.getWorkstream() != null ? task.getWorkstream().getType() : null,
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getSeverity(),
                task.getRequiredRole(),
                task.getAssignedUser() != null ? UserDto.from(task.getAssignedUser()) : null,
                task.isEscalatedToLeads(),
                task.getResolutionNote(),
                task.getDueDate(),
                task.getSourceRuleId(),
                task.getSourceIssueId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
