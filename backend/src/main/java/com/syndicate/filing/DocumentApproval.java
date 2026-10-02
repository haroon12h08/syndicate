package com.syndicate.filing;

import com.syndicate.common.BaseEntity;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.transaction.TransactionRole;
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
 * A named person approving one exact compiled document (spec §4.8). Approvals are never edited:
 * they are granted, and later invalidated with a reason when the document stops being current.
 */
@Entity
@Table(name = "document_approvals")
public class DocumentApproval extends BaseEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drhp_document_id", nullable = false, updatable = false)
    private DrhpDocument document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_user_id", nullable = false, updatable = false)
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, updatable = false)
    private TransactionRole approverRole;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "invalidation_reason")
    private String invalidationReason;

    protected DocumentApproval() {
    }

    public DocumentApproval(UUID transactionId, DrhpDocument document, User approver, TransactionRole approverRole) {
        this.transactionId = transactionId;
        this.document = document;
        this.approver = approver;
        this.approverRole = approverRole;
        this.grantedAt = Instant.now();
    }

    public void invalidate(String reason) {
        if (invalidatedAt == null) {
            this.invalidatedAt = Instant.now();
            this.invalidationReason = reason;
        }
    }

    public boolean isCurrent() {
        return invalidatedAt == null;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public DrhpDocument getDocument() {
        return document;
    }

    public User getApprover() {
        return approver;
    }

    public TransactionRole getApproverRole() {
        return approverRole;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public String getInvalidationReason() {
        return invalidationReason;
    }
}
