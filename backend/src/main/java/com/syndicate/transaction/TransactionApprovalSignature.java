package com.syndicate.transaction;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "transaction_approval_signatures", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"transaction_id", "transition", "required_role"})
})
public class TransactionApprovalSignature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private String transition;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_role", nullable = false)
    private TransactionRole requiredRole;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signed_by_user_id", nullable = false)
    private User signedByUser;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column
    private String comment;

    protected TransactionApprovalSignature() {
    }

    public TransactionApprovalSignature(Transaction transaction, String transition, TransactionRole requiredRole,
                                         User signedByUser, String comment) {
        this.transaction = transaction;
        this.transition = transition;
        this.requiredRole = requiredRole;
        this.signedByUser = signedByUser;
        this.signedAt = Instant.now();
        this.comment = comment;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getTransition() {
        return transition;
    }

    public TransactionRole getRequiredRole() {
        return requiredRole;
    }

    public User getSignedByUser() {
        return signedByUser;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public String getComment() {
        return comment;
    }
}
