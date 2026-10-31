package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Audit log entry for a transaction lifecycle transition (spec §38; plan D6).
 */
@Entity
@Table(name = "transaction_lifecycle_transitions")
public class TransactionLifecycleTransition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "from_state", nullable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "blockers_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String blockersSnapshot = "[]";

    @Column(name = "exceptions_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String exceptionsSnapshot = "[]";

    @Column(name = "transitioned_at", nullable = false)
    private Instant transitionedAt = Instant.now();

    protected TransactionLifecycleTransition() {
    }

    public TransactionLifecycleTransition(Transaction transaction, String fromState, String toState, User actor,
                                          String reason, String blockersSnapshot, String exceptionsSnapshot) {
        this.transaction = transaction;
        this.fromState = fromState;
        this.toState = toState;
        this.actor = actor;
        this.reason = reason;
        this.blockersSnapshot = blockersSnapshot != null ? blockersSnapshot : "[]";
        this.exceptionsSnapshot = exceptionsSnapshot != null ? exceptionsSnapshot : "[]";
        this.transitionedAt = Instant.now();
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public String getFromState() {
        return fromState;
    }

    public void setFromState(String fromState) {
        this.fromState = fromState;
    }

    public String getToState() {
        return toState;
    }

    public void setToState(String toState) {
        this.toState = toState;
    }

    public User getActor() {
        return actor;
    }

    public void setActor(User actor) {
        this.actor = actor;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getBlockersSnapshot() {
        return blockersSnapshot;
    }

    public void setBlockersSnapshot(String blockersSnapshot) {
        this.blockersSnapshot = blockersSnapshot;
    }

    public String getExceptionsSnapshot() {
        return exceptionsSnapshot;
    }

    public void setExceptionsSnapshot(String exceptionsSnapshot) {
        this.exceptionsSnapshot = exceptionsSnapshot;
    }

    public Instant getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(Instant transitionedAt) {
        this.transitionedAt = transitionedAt;
    }
}
