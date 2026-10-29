package com.syndicate.ai.suggestion;

import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_suggestions")
public class AiSuggestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workstream_id")
    private Workstream workstream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_artifact_id")
    private AiArtifact aiArtifact;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_type", nullable = false, length = 64)
    private SuggestionType suggestionType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SuggestionSeverity severity = SuggestionSeverity.INFO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "action_payload", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actionPayload = "{}";

    @Column(name = "dismiss_reason", columnDefinition = "TEXT")
    private String dismissReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedByUser;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    protected AiSuggestion() {}

    public AiSuggestion(Transaction transaction, Workstream workstream, AiArtifact aiArtifact,
                        SuggestionType suggestionType, String title, String description,
                        SuggestionSeverity severity, String targetType, UUID targetId,
                        String actionType, String actionPayload) {
        this.transaction = transaction;
        this.workstream = workstream;
        this.aiArtifact = aiArtifact;
        this.suggestionType = suggestionType;
        this.title = title;
        this.description = description;
        this.severity = severity != null ? severity : SuggestionSeverity.INFO;
        this.status = SuggestionStatus.PENDING;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actionType = actionType;
        this.actionPayload = actionPayload != null ? actionPayload : "{}";
    }

    public void accept(User reviewer) {
        this.status = SuggestionStatus.ACCEPTED;
        this.reviewedByUser = reviewer;
        this.reviewedAt = Instant.now();
        this.appliedAt = Instant.now();
        if (this.aiArtifact != null) {
            this.aiArtifact.accept(reviewer, "Accepted suggestion: " + this.title, "AiSuggestion", this.getId());
        }
    }

    public void dismiss(User reviewer, String reason) {
        this.status = SuggestionStatus.DISMISSED;
        this.reviewedByUser = reviewer;
        this.reviewedAt = Instant.now();
        this.dismissReason = reason;
        if (this.aiArtifact != null) {
            this.aiArtifact.reject(reviewer, reason != null ? reason : "Dismissed suggestion");
        }
    }

    // Getters and Setters
    public Transaction getTransaction() { return transaction; }
    public Workstream getWorkstream() { return workstream; }
    public AiArtifact getAiArtifact() { return aiArtifact; }
    public void setAiArtifact(AiArtifact aiArtifact) { this.aiArtifact = aiArtifact; }
    public SuggestionType getSuggestionType() { return suggestionType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public SuggestionSeverity getSeverity() { return severity; }
    public SuggestionStatus getStatus() { return status; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getActionType() { return actionType; }
    public String getActionPayload() { return actionPayload; }
    public String getDismissReason() { return dismissReason; }
    public User getReviewedByUser() { return reviewedByUser; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getAppliedAt() { return appliedAt; }
}
