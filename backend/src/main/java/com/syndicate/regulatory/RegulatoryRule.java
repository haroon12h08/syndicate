package com.syndicate.regulatory;

import com.syndicate.common.BaseEntity;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.workstream.WorkstreamType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

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

    @Column(name = "fact_label_pattern", nullable = false)
    private String factLabelPattern;

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

    public String getFactLabelPattern() {
        return factLabelPattern;
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
