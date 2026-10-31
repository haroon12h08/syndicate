package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
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
import java.time.LocalDate;

/**
 * Immutable version of a regulatory requirement (spec §13, §57; plan D1).
 * Carries verified legal citation, source URL, effective interval, expression, and multi-eye approval state.
 */
@Entity
@Table(name = "requirement_versions")
public class RequirementVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 512)
    private String citation;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "source_document_hash", length = 64)
    private String sourceDocumentHash;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "applicability_expression", columnDefinition = "TEXT")
    private String applicabilityExpression;

    @Column(name = "required_facts", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requiredFacts = "[]";

    @Column(name = "required_evidence_types", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requiredEvidenceTypes = "[]";

    @Column(name = "required_reviews", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requiredReviews = "[]";

    @Column(name = "required_approvals", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requiredApprovals = "[]";

    @Column(name = "remediation_guide", columnDefinition = "TEXT")
    private String remediationGuide;

    @Column(name = "rule_expression", columnDefinition = "TEXT")
    private String ruleExpression;

    @Column(nullable = false)
    private String status = "ACTIVE"; // DRAFT, PENDING_APPROVAL, ACTIVE, REJECTED, UNSOURCED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected RequirementVersion() {
    }

    public RequirementVersion(Requirement requirement, int versionNumber, String citation, String sourceUrl,
                              LocalDate effectiveFrom, User author) {
        this.requirement = requirement;
        this.versionNumber = versionNumber;
        this.citation = citation;
        this.sourceUrl = sourceUrl;
        this.effectiveFrom = effectiveFrom;
        this.author = author;
        this.status = (sourceUrl == null || sourceUrl.isBlank()) ? "UNSOURCED" : "DRAFT";
    }

    public boolean isUnsourced() {
        return "UNSOURCED".equalsIgnoreCase(status) || sourceUrl == null || sourceUrl.isBlank();
    }

    public Requirement getRequirement() {
        return requirement;
    }

    public void setRequirement(Requirement requirement) {
        this.requirement = requirement;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getCitation() {
        return citation;
    }

    public void setCitation(String citation) {
        this.citation = citation;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceDocumentHash() {
        return sourceDocumentHash;
    }

    public void setSourceDocumentHash(String sourceDocumentHash) {
        this.sourceDocumentHash = sourceDocumentHash;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getApplicabilityExpression() {
        return applicabilityExpression;
    }

    public void setApplicabilityExpression(String applicabilityExpression) {
        this.applicabilityExpression = applicabilityExpression;
    }

    public String getRequiredFacts() {
        return requiredFacts;
    }

    public void setRequiredFacts(String requiredFacts) {
        this.requiredFacts = requiredFacts;
    }

    public String getRequiredEvidenceTypes() {
        return requiredEvidenceTypes;
    }

    public void setRequiredEvidenceTypes(String requiredEvidenceTypes) {
        this.requiredEvidenceTypes = requiredEvidenceTypes;
    }

    public String getRequiredReviews() {
        return requiredReviews;
    }

    public void setRequiredReviews(String requiredReviews) {
        this.requiredReviews = requiredReviews;
    }

    public String getRequiredApprovals() {
        return requiredApprovals;
    }

    public void setRequiredApprovals(String requiredApprovals) {
        this.requiredApprovals = requiredApprovals;
    }

    public String getRemediationGuide() {
        return remediationGuide;
    }

    public void setRemediationGuide(String remediationGuide) {
        this.remediationGuide = remediationGuide;
    }

    public String getRuleExpression() {
        return ruleExpression;
    }

    public void setRuleExpression(String ruleExpression) {
        this.ruleExpression = ruleExpression;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}
