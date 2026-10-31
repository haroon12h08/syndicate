package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
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
 * Fixture-based test case verifying a requirement's expression before approval (spec §57; plan D4).
 */
@Entity
@Table(name = "requirement_test_cases")
public class RequirementTestCase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_version_id", nullable = false)
    private RequirementVersion requirementVersion;

    @Column(nullable = false)
    private String name;

    @Column(name = "fixture_inputs", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String fixtureInputs = "{}";

    @Column(name = "expected_status", nullable = false)
    private String expectedStatus; // PASS, FAIL, MISSING, NOT_APPLICABLE

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_passed")
    private Boolean lastRunPassed;

    protected RequirementTestCase() {
    }

    public RequirementTestCase(RequirementVersion requirementVersion, String name, String fixtureInputs, String expectedStatus) {
        this.requirementVersion = requirementVersion;
        this.name = name;
        this.fixtureInputs = fixtureInputs;
        this.expectedStatus = expectedStatus;
    }

    public RequirementVersion getRequirementVersion() {
        return requirementVersion;
    }

    public void setRequirementVersion(RequirementVersion requirementVersion) {
        this.requirementVersion = requirementVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFixtureInputs() {
        return fixtureInputs;
    }

    public void setFixtureInputs(String fixtureInputs) {
        this.fixtureInputs = fixtureInputs;
    }

    public String getExpectedStatus() {
        return expectedStatus;
    }

    public void setExpectedStatus(String expectedStatus) {
        this.expectedStatus = expectedStatus;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public Boolean getLastRunPassed() {
        return lastRunPassed;
    }

    public void setLastRunPassed(Boolean lastRunPassed) {
        this.lastRunPassed = lastRunPassed;
    }
}
