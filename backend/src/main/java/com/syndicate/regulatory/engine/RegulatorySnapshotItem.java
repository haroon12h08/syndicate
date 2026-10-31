package com.syndicate.regulatory.engine;

import com.syndicate.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Join table linking a pinned snapshot to an immutable RequirementVersion.
 */
@Entity
@Table(name = "regulatory_snapshot_items")
public class RegulatorySnapshotItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private RegulatorySnapshot snapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_version_id", nullable = false)
    private RequirementVersion requirementVersion;

    protected RegulatorySnapshotItem() {
    }

    public RegulatorySnapshotItem(RegulatorySnapshot snapshot, RequirementVersion requirementVersion) {
        this.snapshot = snapshot;
        this.requirementVersion = requirementVersion;
    }

    public RegulatorySnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(RegulatorySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public RequirementVersion getRequirementVersion() {
        return requirementVersion;
    }

    public void setRequirementVersion(RequirementVersion requirementVersion) {
        this.requirementVersion = requirementVersion;
    }
}
