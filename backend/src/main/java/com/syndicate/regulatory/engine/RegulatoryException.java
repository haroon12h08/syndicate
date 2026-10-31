package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.transaction.Transaction;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Transparent statutory or policy exception (spec §39, invariant 19; plan D5).
 * Enables transactions to document and authorize exemptions instead of forcing teams to falsify facts.
 */
@Entity
@Table(name = "regulatory_exceptions")
public class RegulatoryException extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "control_id", nullable = false)
    private RegulatoryControl control;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_version_id", nullable = false)
    private RequirementVersion requirementVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "affected_state", nullable = false)
    private String affectedState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supporting_evidence_id")
    private Evidence supportingEvidence;

    @Column(name = "risk_classification", nullable = false)
    private String riskClassification = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(nullable = false)
    private String status = "REQUESTED"; // REQUESTED, APPROVED, REJECTED, EXPIRED, INVALIDATED

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "mitigation_plan", nullable = false, columnDefinition = "TEXT")
    private String mitigationPlan;

    protected RegulatoryException() {
    }

    public RegulatoryException(Transaction transaction, RegulatoryControl control, RequirementVersion requirementVersion,
                               String reason, String affectedState, Evidence supportingEvidence, String riskClassification,
                               User owner, LocalDate expiryDate, String mitigationPlan) {
        this.transaction = transaction;
        this.control = control;
        this.requirementVersion = requirementVersion;
        this.reason = reason;
        this.affectedState = affectedState;
        this.supportingEvidence = supportingEvidence;
        this.riskClassification = riskClassification != null ? riskClassification : "MEDIUM";
        this.owner = owner;
        this.status = "REQUESTED";
        this.expiryDate = expiryDate;
        this.mitigationPlan = mitigationPlan;
    }

    public boolean isCurrentlyEffective() {
        if (!"APPROVED".equalsIgnoreCase(status)) {
            return false;
        }
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            return false;
        }
        return true;
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

    public RequirementVersion getRequirementVersion() {
        return requirementVersion;
    }

    public void setRequirementVersion(RequirementVersion requirementVersion) {
        this.requirementVersion = requirementVersion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAffectedState() {
        return affectedState;
    }

    public void setAffectedState(String affectedState) {
        this.affectedState = affectedState;
    }

    public Evidence getSupportingEvidence() {
        return supportingEvidence;
    }

    public void setSupportingEvidence(Evidence supportingEvidence) {
        this.supportingEvidence = supportingEvidence;
    }

    public String getRiskClassification() {
        return riskClassification;
    }

    public void setRiskClassification(String riskClassification) {
        this.riskClassification = riskClassification;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public User getApprover() {
        return approver;
    }

    public void setApprover(User approver) {
        this.approver = approver;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getMitigationPlan() {
        return mitigationPlan;
    }

    public void setMitigationPlan(String mitigationPlan) {
        this.mitigationPlan = mitigationPlan;
    }
}
