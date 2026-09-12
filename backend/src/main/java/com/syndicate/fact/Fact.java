package com.syndicate.fact;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "facts")
public class Fact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstream_id", nullable = false)
    private Workstream workstream;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String value;

    @Column
    private String unit;

    @Column
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FactStatus status;

    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_fact_id")
    private Fact supersedesFact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedByUser;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "system_superseded_at")
    private Instant systemSupersededAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "fact_evidence_link",
            joinColumns = @JoinColumn(name = "fact_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_id")
    )
    private Set<Evidence> evidence = new HashSet<>();

    protected Fact() {
    }

    public Fact(Workstream workstream, String label, String value, String unit, String period,
                Fact supersedesFact, int version, User createdByUser, Instant validFrom, Instant validTo) {
        this.workstream = workstream;
        this.label = label;
        this.value = value;
        this.unit = unit;
        this.period = period;
        this.status = FactStatus.DRAFT;
        this.version = version;
        this.supersedesFact = supersedesFact;
        this.createdByUser = createdByUser;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public String getPeriod() {
        return period;
    }

    public FactStatus getStatus() {
        return status;
    }

    public void setStatus(FactStatus status) {
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public Fact getSupersedesFact() {
        return supersedesFact;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public User getVerifiedByUser() {
        return verifiedByUser;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void markVerified(User verifier, Instant at) {
        this.status = FactStatus.VERIFIED;
        this.verifiedByUser = verifier;
        this.verifiedAt = at;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Instant getSystemSupersededAt() {
        return systemSupersededAt;
    }

    /**
     * Closes both time axes: system time always closes now, business time only if it was
     * still open — an explicitly backdated valid_to set earlier is never overwritten.
     */
    public void markSuperseded(Instant supersededAt, Instant businessValidUntil) {
        this.status = FactStatus.SUPERSEDED;
        this.systemSupersededAt = supersededAt;
        if (this.validTo == null) {
            this.validTo = businessValidUntil;
        }
    }

    public Set<Evidence> getEvidence() {
        return evidence;
    }
}
