package com.syndicate.regulatory;

import com.syndicate.common.BaseEntity;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.workstream.WorkstreamType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "regulatory_rules")
public class RegulatoryRule extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "workstream_type", nullable = false)
    private WorkstreamType workstreamType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSeverity severity;

    @Column(name = "fact_key", nullable = false)
    private String factKey;

    @Column(name = "source_authority")
    private String sourceAuthority;

    @Column(name = "source_citation")
    private String sourceCitation;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private int version;

    /** INTERNAL_POLICY means this is a configured check, not a statement of regulation. */
    @Column(name = "provenance_status", nullable = false)
    private String provenanceStatus;

    @Column(name = "minimum_facts", nullable = false)
    private int minimumFacts;

    @Column(nullable = false)
    private String expression;

    @Column(name = "requirement_summary", nullable = false)
    private String requirementSummary;

    @Column(nullable = false)
    private boolean active;

    protected RegulatoryRule() {
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public WorkstreamType getWorkstreamType() {
        return workstreamType;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public String getFactKey() {
        return factKey;
    }

    public String getSourceAuthority() {
        return sourceAuthority;
    }

    public String getSourceCitation() {
        return sourceCitation;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public int getVersion() {
        return version;
    }

    public String getProvenanceStatus() {
        return provenanceStatus;
    }

    public int getMinimumFacts() {
        return minimumFacts;
    }

    public String getExpression() {
        return expression;
    }

    public String getRequirementSummary() {
        return requirementSummary;
    }

    public boolean isActive() {
        return active;
    }
}
