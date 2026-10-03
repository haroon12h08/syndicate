package com.syndicate.diligence;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.user.User;
import com.syndicate.workstream.WorkstreamType;
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
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One question the transaction has to answer, and the state of that answer (spec §9). */
@Entity
@Table(name = "diligence_questions")
public class DiligenceQuestion extends BaseEntity {

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    /** Null for a question this team added themselves. */
    @Column(updatable = false)
    private String code;

    @Column(nullable = false)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "workstream_type", nullable = false)
    private WorkstreamType workstreamType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiligenceStatus status = DiligenceStatus.OPEN;

    @Column
    private String answer;

    @Column(name = "not_applicable_reason")
    private String notApplicableReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by_user_id")
    private User answeredBy;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "diligence_question_facts",
            joinColumns = @JoinColumn(name = "question_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id"))
    private Set<Fact> supportingFacts = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "diligence_question_evidence",
            joinColumns = @JoinColumn(name = "question_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_id"))
    private Set<Evidence> supportingEvidence = new HashSet<>();

    protected DiligenceQuestion() {
    }

    public DiligenceQuestion(UUID transactionId, String code, String question, WorkstreamType workstreamType,
                             IssueSeverity severity) {
        this.transactionId = transactionId;
        this.code = code;
        this.question = question;
        this.workstreamType = workstreamType;
        this.severity = severity;
    }

    public void answer(String answer, User answeredBy) {
        this.answer = answer;
        this.answeredBy = answeredBy;
        this.answeredAt = Instant.now();
        this.status = DiligenceStatus.ANSWERED;
        this.notApplicableReason = null;
    }

    public void accept() {
        this.status = DiligenceStatus.ACCEPTED;
    }

    public void reopen() {
        this.status = DiligenceStatus.ANSWERED;
    }

    public void markNotApplicable(String reason, User by) {
        this.status = DiligenceStatus.NOT_APPLICABLE;
        this.notApplicableReason = reason;
        this.answeredBy = by;
        this.answeredAt = Instant.now();
    }

    public void assign(User owner, LocalDate dueDate) {
        this.owner = owner;
        this.dueDate = dueDate;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getCode() {
        return code;
    }

    public String getQuestion() {
        return question;
    }

    public WorkstreamType getWorkstreamType() {
        return workstreamType;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public DiligenceStatus getStatus() {
        return status;
    }

    public String getAnswer() {
        return answer;
    }

    public String getNotApplicableReason() {
        return notApplicableReason;
    }

    public User getOwner() {
        return owner;
    }

    public User getAnsweredBy() {
        return answeredBy;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Set<Fact> getSupportingFacts() {
        return supportingFacts;
    }

    public Set<Evidence> getSupportingEvidence() {
        return supportingEvidence;
    }
}
