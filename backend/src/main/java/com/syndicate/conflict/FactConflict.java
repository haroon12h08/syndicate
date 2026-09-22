package com.syndicate.conflict;

import com.syndicate.common.BaseEntity;
import com.syndicate.fact.Fact;
import com.syndicate.issue.Issue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "fact_conflicts")
public class FactConflict extends BaseEntity {

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "conflict_key", nullable = false, updatable = false)
    private String conflictKey;

    @Column(name = "fact_key", nullable = false, updatable = false)
    private String factKey;

    @Column(updatable = false)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConflictStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private Issue issue;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "fact_conflict_members",
            joinColumns = @JoinColumn(name = "conflict_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id"))
    private Set<Fact> members = new HashSet<>();

    @OneToMany(mappedBy = "conflict", fetch = FetchType.LAZY)
    @OrderBy("resolvedAt ASC")
    private List<ConflictResolution> resolutions = new ArrayList<>();

    protected FactConflict() {
    }

    public FactConflict(UUID transactionId, String conflictKey, String factKey, String period, Issue issue) {
        this.transactionId = transactionId;
        this.conflictKey = conflictKey;
        this.factKey = factKey;
        this.period = period;
        this.issue = issue;
        this.status = ConflictStatus.OPEN;
        this.detectedAt = Instant.now();
    }

    public void reopen(Set<Fact> competing) {
        if (status != ConflictStatus.OPEN) {
            status = ConflictStatus.OPEN;
            detectedAt = Instant.now();
            closedAt = null;
        }
        members.clear();
        members.addAll(competing);
    }

    public void clear() {
        status = ConflictStatus.CLEARED;
        closedAt = Instant.now();
    }

    public void markResolved(Instant at) {
        status = ConflictStatus.RESOLVED;
        closedAt = at;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getConflictKey() {
        return conflictKey;
    }

    public String getFactKey() {
        return factKey;
    }

    public String getPeriod() {
        return period;
    }

    public ConflictStatus getStatus() {
        return status;
    }

    public Issue getIssue() {
        return issue;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Set<Fact> getMembers() {
        return members;
    }

    public List<ConflictResolution> getResolutions() {
        return resolutions;
    }
}
