package com.syndicate.issue;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "issues")
public class Issue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstream_id", nullable = false)
    private Workstream workstream;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "issue_fact_link",
            joinColumns = @JoinColumn(name = "issue_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id")
    )
    private Set<Fact> relatedFacts = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "issue_evidence_link",
            joinColumns = @JoinColumn(name = "issue_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_id")
    )
    private Set<Evidence> relatedEvidence = new HashSet<>();

    protected Issue() {
    }

    public Issue(Workstream workstream, String title, String description, IssueSeverity severity,
                 User ownerUser, LocalDate dueDate, User createdByUser) {
        this.workstream = workstream;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.status = IssueStatus.OPEN;
        this.ownerUser = ownerUser;
        this.dueDate = dueDate;
        this.createdByUser = createdByUser;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getResolution() {
        return resolution;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public Set<Fact> getRelatedFacts() {
        return relatedFacts;
    }

    public Set<Evidence> getRelatedEvidence() {
        return relatedEvidence;
    }

    public void update(String title, String description, IssueSeverity severity, IssueStatus status,
                        User ownerUser, LocalDate dueDate, String resolution) {
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.status = status;
        this.ownerUser = ownerUser;
        this.dueDate = dueDate;
        this.resolution = resolution;
    }
}
