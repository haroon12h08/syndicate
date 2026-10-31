package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Deterministic or procedural control bound to a RequirementVersion (spec §4.6; plan D2).
 */
@Entity
@Table(name = "regulatory_controls")
public class RegulatoryControl extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_version_id", nullable = false)
    private RequirementVersion requirementVersion;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "control_type", nullable = false)
    private String controlType; // DETERMINISTIC_EXPRESSION, EVIDENCE_PRESENCE, FACT_VERIFICATION, APPROVAL_CHECK, DOMAIN_RECONCILIATION

    @Column(columnDefinition = "TEXT")
    private String expression;

    @Column(nullable = false)
    private String severity = "HIGH";

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    protected RegulatoryControl() {
    }

    public RegulatoryControl(RequirementVersion requirementVersion, String code, String name, String description,
                             String controlType, String expression, String severity, int orderIndex) {
        this.requirementVersion = requirementVersion;
        this.code = code;
        this.name = name;
        this.description = description;
        this.controlType = controlType;
        this.expression = expression;
        this.severity = severity != null ? severity : "HIGH";
        this.orderIndex = orderIndex;
    }

    public RequirementVersion getRequirementVersion() {
        return requirementVersion;
    }

    public void setRequirementVersion(RequirementVersion requirementVersion) {
        this.requirementVersion = requirementVersion;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getControlType() {
        return controlType;
    }

    public void setControlType(String controlType) {
        this.controlType = controlType;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
