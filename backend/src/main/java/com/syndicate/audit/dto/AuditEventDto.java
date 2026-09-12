package com.syndicate.audit.dto;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDto(
        UUID id,
        UUID transactionId,
        String actorName,
        AuditAction action,
        String entityType,
        UUID entityId,
        String summary,
        String previousValue,
        String newValue,
        String reason,
        Instant occurredAt
) {
    public static AuditEventDto from(AuditEvent e) {
        return new AuditEventDto(e.getId(), e.getTransactionId(),
                e.getActorUser() != null ? e.getActorUser().getFullName() : "System",
                e.getAction(), e.getEntityType(), e.getEntityId(), e.getSummary(),
                e.getPreviousValue(), e.getNewValue(), e.getReason(), e.getOccurredAt());
    }
}
