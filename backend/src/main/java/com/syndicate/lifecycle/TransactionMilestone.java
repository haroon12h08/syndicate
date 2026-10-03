package com.syndicate.lifecycle;

import com.syndicate.common.BaseEntity;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A recorded act. Syndicate never claims one of these happened on its own (spec §36). */
@Entity
@Immutable
@Table(name = "transaction_milestones")
public class TransactionMilestone extends BaseEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MilestoneType type;

    @Column(name = "occurred_on", nullable = false, updatable = false)
    private LocalDate occurredOn;

    @Column(updatable = false)
    private String reference;

    @Column(updatable = false)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drhp_document_id", updatable = false)
    private DrhpDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false, updatable = false)
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected TransactionMilestone() {
    }

    public TransactionMilestone(UUID transactionId, MilestoneType type, LocalDate occurredOn, String reference,
                                String notes, DrhpDocument document, User recordedBy) {
        this.transactionId = transactionId;
        this.type = type;
        this.occurredOn = occurredOn;
        this.reference = reference;
        this.notes = notes;
        this.document = document;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public MilestoneType getType() {
        return type;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }

    public DrhpDocument getDocument() {
        return document;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
