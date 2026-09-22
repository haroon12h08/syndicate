package com.syndicate.conflict;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** One human decision on a conflict (spec §11: chosen, rejected, reason, reviewer, evidence, time). */
@Entity
@Table(name = "fact_conflict_resolutions")
public class ConflictResolution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conflict_id", nullable = false, updatable = false)
    private FactConflict conflict;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chosen_fact_id", nullable = false, updatable = false)
    private Fact chosenFact;

    @Column(nullable = false, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resolved_by_user_id", nullable = false, updatable = false)
    private User resolvedBy;

    @Column(name = "resolved_at", nullable = false, updatable = false)
    private Instant resolvedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "fact_conflict_resolution_rejected",
            joinColumns = @JoinColumn(name = "resolution_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id"))
    private Set<Fact> rejectedFacts = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "fact_conflict_resolution_evidence",
            joinColumns = @JoinColumn(name = "resolution_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_id"))
    private Set<Evidence> evidenceConsidered = new HashSet<>();

    protected ConflictResolution() {
    }

    public ConflictResolution(FactConflict conflict, Fact chosenFact, Set<Fact> rejectedFacts,
                              Set<Evidence> evidenceConsidered, String reason, User resolvedBy, Instant resolvedAt) {
        this.conflict = conflict;
        this.chosenFact = chosenFact;
        this.rejectedFacts.addAll(rejectedFacts);
        this.evidenceConsidered.addAll(evidenceConsidered);
        this.reason = reason;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    public FactConflict getConflict() {
        return conflict;
    }

    public Fact getChosenFact() {
        return chosenFact;
    }

    public String getReason() {
        return reason;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Set<Fact> getRejectedFacts() {
        return rejectedFacts;
    }

    public Set<Evidence> getEvidenceConsidered() {
        return evidenceConsidered;
    }
}
