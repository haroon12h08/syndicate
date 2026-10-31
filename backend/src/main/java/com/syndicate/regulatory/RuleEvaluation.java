package com.syndicate.regulatory;

import com.syndicate.common.BaseEntity;
import com.syndicate.fact.Fact;
import com.syndicate.issue.Issue;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rule_evaluations")
public class RuleEvaluation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private RegulatoryRule rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleEvaluationStatus status;

    @Column(name = "actual_value")
    private String actualValue;

    @Column
    private String detail;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private java.util.UUID entityId;

    @Column(name = "inputs_snapshot", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String inputsSnapshot;

    @Column(name = "formula_expression")
    private String formulaExpression;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_issue_id")
    private Issue generatedIssue;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rule_evaluation_fact_link",
            joinColumns = @JoinColumn(name = "rule_evaluation_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id")
    )
    private Set<Fact> supportingFacts = new HashSet<>();

    protected RuleEvaluation() {
    }

    public RuleEvaluation(Transaction transaction, RegulatoryRule rule) {
        this.transaction = transaction;
        this.rule = rule;
        this.status = RuleEvaluationStatus.NOT_APPLICABLE;
        this.evaluatedAt = Instant.now();
    }

    public RuleEvaluation(Transaction transaction, RegulatoryRule rule, RuleEvaluationStatus status,
                          String actualValue, String detail) {
        this.transaction = transaction;
        this.rule = rule;
        this.status = status != null ? status : RuleEvaluationStatus.NOT_APPLICABLE;
        this.actualValue = actualValue;
        this.detail = detail;
        this.evaluatedAt = Instant.now();
    }

    public void setStatus(RuleEvaluationStatus status) {
        this.status = status;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public void record(RuleEvaluationStatus status, String actualValue, String detail,
                       Set<Fact> supportingFacts, Instant evaluatedAt) {
        this.status = status;
        this.actualValue = actualValue;
        this.detail = detail;
        this.evaluatedAt = evaluatedAt;
        this.supportingFacts.clear();
        this.supportingFacts.addAll(supportingFacts);
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public RegulatoryRule getRule() {
        return rule;
    }

    public RuleEvaluationStatus getStatus() {
        return status;
    }

    public String getActualValue() {
        return actualValue;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public java.util.UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(java.util.UUID entityId) {
        this.entityId = entityId;
    }

    public String getInputsSnapshot() {
        return inputsSnapshot;
    }

    public void setInputsSnapshot(String inputsSnapshot) {
        this.inputsSnapshot = inputsSnapshot;
    }

    public String getFormulaExpression() {
        return formulaExpression;
    }

    public void setFormulaExpression(String formulaExpression) {
        this.formulaExpression = formulaExpression;
    }

    public Issue getGeneratedIssue() {
        return generatedIssue;
    }

    public void setGeneratedIssue(Issue generatedIssue) {
        this.generatedIssue = generatedIssue;
    }

    public Set<Fact> getSupportingFacts() {
        return supportingFacts;
    }
}
