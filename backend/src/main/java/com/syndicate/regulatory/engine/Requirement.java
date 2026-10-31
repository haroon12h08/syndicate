package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * High-level regulatory or governance requirement (spec §13; plan D1).
 * Carries jurisdiction, authority, title, and default severity.
 * Concrete statutory citations, expressions, and required dependencies live in {@link RequirementVersion}.
 */
@Entity
@Table(name = "requirements")
public class Requirement extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String authority;

    @Column(nullable = false)
    private String jurisdiction = "INDIA";

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType = "ALL";

    @Column(name = "issue_type", nullable = false)
    private String issueType = "ALL";

    @Column(nullable = false)
    private String severity = "HIGH";

    @Column(nullable = false)
    private String status = "ACTIVE";

    protected Requirement() {
    }

    public Requirement(String code, String authority, String jurisdiction, String title, String description,
                       String transactionType, String issueType, String severity, String status) {
        this.code = code;
        this.authority = authority;
        this.jurisdiction = jurisdiction != null ? jurisdiction : "INDIA";
        this.title = title;
        this.description = description;
        this.transactionType = transactionType != null ? transactionType : "ALL";
        this.issueType = issueType != null ? issueType : "ALL";
        this.severity = severity != null ? severity : "HIGH";
        this.status = status != null ? status : "ACTIVE";
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
