package com.syndicate.task.dto;

import com.syndicate.task.TaskStatus;

import java.util.UUID;

/** Every field is optional: this is a PATCH, so only what is present changes. */
public record UpdateTaskRequest(
        TaskStatus status,
        UUID assignedUserId,
        String resolutionNote
) {
}
