package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
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
 * Result of evaluating a RegulatoryControl against a transaction's verified state (spec §4.6; plan D2).
 * Explanation strictly obeys Invariant 18 / §13 boundary.
 */
@Entity
@Table(name = "control_evaluations")
public class ControlEvaluation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_id", nullable = false)
    private RegulatoryControl control;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id")
    private RegulatorySnapshot snapshot;

    @Column(nullable = false)
    private String status; // PASS, FAIL, MISSING, NOT_APPLICABLE, NEEDS_REVIEW, EXCEPTION

    @Column(name = "inputs_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputsSnapshot = "{}";

    @Column(name = "expression_evaluated", columnDefinition = "TEXT")
    private String expressionEvaluated;

    @Column(name = "result_value")
    private String resultValue;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exception_id")
    private RegulatoryException exception;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt = Instant.now();

    protected ControlEvaluation() {
    }

    public ControlEvaluation(Transaction transaction, RegulatoryControl control, RegulatorySnapshot snapshot,
                             String status, String inputsSnapshot, String expressionEvaluated, String resultValue,
                             String explanation, RegulatoryException exception) {
        this.transaction = transaction;
        this.control = control;
        this.snapshot = snapshot;
        this.status = status;
        this.inputsSnapshot = inputsSnapshot != null ? inputsSnapshot : "{}";
        this.expressionEvaluated = expressionEvaluated;
        this.resultValue = resultValue;
        this.explanation = explanation;
        this.exception = exception;
        this.evaluatedAt = Instant.now();
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public RegulatoryControl getControl() {
        return control;
    }

    public void setControl(RegulatoryControl control) {
        this.control = control;
    }

    public RegulatorySnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(RegulatorySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInputsSnapshot() {
        return inputsSnapshot;
    }

    public void setInputsSnapshot(String inputsSnapshot) {
        this.inputsSnapshot = inputsSnapshot;
    }

    public String getExpressionEvaluated() {
        return expressionEvaluated;
    }

    public void setExpressionEvaluated(String expressionEvaluated) {
        this.expressionEvaluated = expressionEvaluated;
    }

    public String getResultValue() {
        return resultValue;
    }

    public void setResultValue(String resultValue) {
        this.resultValue = resultValue;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public RegulatoryException getException() {
        return exception;
    }

    public void setException(RegulatoryException exception) {
        this.exception = exception;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
