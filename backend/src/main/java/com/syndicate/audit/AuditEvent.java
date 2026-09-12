package com.syndicate.audit;

import com.syndicate.common.BaseEntity;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded mutation. Append-only from the application's perspective: this entity exposes no
 * setters and no service ever updates or deletes a row, so the trail cannot be rewritten.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {

    @Column(name = "transaction_id")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(nullable = false)
    private String summary;

    @Column(name = "previous_value")
    private String previousValue;

    @Column(name = "new_value")
    private String newValue;

    @Column
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID transactionId, User actorUser, AuditAction action, String entityType,
                      UUID entityId, String summary, String previousValue, String newValue, String reason) {
        this.transactionId = transactionId;
        this.actorUser = actorUser;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.reason = reason;
        this.occurredAt = Instant.now();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public User getActorUser() {
        return actorUser;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
