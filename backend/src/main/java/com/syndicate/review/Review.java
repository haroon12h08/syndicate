package com.syndicate.review;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.User;
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
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A human evaluation of one exact version of an object (spec §4.7). Immutable once recorded. */
@Entity
@Immutable
@Table(name = "reviews")
public class Review extends BaseEntity {

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReviewTargetType targetType;

    @Column(name = "target_version_id", nullable = false)
    private UUID targetVersionId;

    @Column(name = "target_lineage_id", nullable = false)
    private UUID targetLineageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_user_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_role", nullable = false)
    private TransactionRole reviewerRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewDecision decision;

    @Column
    private String comments;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "review_evidence",
            joinColumns = @JoinColumn(name = "review_id"),
            inverseJoinColumns = @JoinColumn(name = "evidence_id"))
    private Set<Evidence> evidenceConsidered = new HashSet<>();

    protected Review() {
    }

    public Review(UUID transactionId, ReviewTargetType targetType, UUID targetVersionId, UUID targetLineageId,
                  User reviewer, TransactionRole reviewerRole, ReviewDecision decision, String comments,
                  Set<Evidence> evidenceConsidered) {
        this.transactionId = transactionId;
        this.targetType = targetType;
        this.targetVersionId = targetVersionId;
        this.targetLineageId = targetLineageId;
        this.reviewer = reviewer;
        this.reviewerRole = reviewerRole;
        this.decision = decision;
        this.comments = comments;
        this.evidenceConsidered.addAll(evidenceConsidered);
        this.reviewedAt = Instant.now();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public ReviewTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetVersionId() {
        return targetVersionId;
    }

    public UUID getTargetLineageId() {
        return targetLineageId;
    }

    public User getReviewer() {
        return reviewer;
    }

    public TransactionRole getReviewerRole() {
        return reviewerRole;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getComments() {
        return comments;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Set<Evidence> getEvidenceConsidered() {
        return evidenceConsidered;
    }
}
