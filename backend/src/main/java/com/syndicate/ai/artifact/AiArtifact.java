package com.syndicate.ai.artifact;

import com.syndicate.common.BaseEntity;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_artifacts")
public class AiArtifact extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "prompt_template_name", nullable = false, length = 128)
    private String promptTemplateName;

    @Column(name = "prompt_template_version", nullable = false, length = 64)
    private String promptTemplateVersion;

    @Column(name = "prompt_template_hash", nullable = false, length = 64)
    private String promptTemplateHash;

    @Column(name = "input_entity_ids", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputEntityIds = "[]";

    @Column(name = "input_hashes", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputHashes = "{}";

    @Column(name = "raw_output", nullable = false, columnDefinition = "TEXT")
    private String rawOutput;

    @Column(name = "structured_output", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String structuredOutput;

    @Column
    private Double confidence;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_cost_usd", precision = 10, scale = 6)
    private BigDecimal totalCostUsd;

    @Column(name = "duration_ms")
    private Long durationMs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(name = "review_decision", length = 32)
    private String reviewDecision = "PENDING";

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "resulting_entity_type", length = 64)
    private String resultingEntityType;

    @Column(name = "resulting_entity_id")
    private UUID resultingEntityId;

    protected AiArtifact() {
    }

    public AiArtifact(String provider, String model, String modelVersion,
                      String promptTemplateName, String promptTemplateVersion, String promptTemplateHash,
                      String inputEntityIds, String inputHashes,
                      String rawOutput, String structuredOutput, Double confidence,
                      Integer promptTokens, Integer completionTokens, BigDecimal totalCostUsd, Long durationMs) {
        this.provider = provider;
        this.model = model;
        this.modelVersion = modelVersion;
        this.promptTemplateName = promptTemplateName;
        this.promptTemplateVersion = promptTemplateVersion;
        this.promptTemplateHash = promptTemplateHash;
        this.inputEntityIds = inputEntityIds != null ? inputEntityIds : "[]";
        this.inputHashes = inputHashes != null ? inputHashes : "{}";
        this.rawOutput = rawOutput;
        this.structuredOutput = structuredOutput;
        this.confidence = confidence;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalCostUsd = totalCostUsd;
        this.durationMs = durationMs;
        this.reviewDecision = "PENDING";
    }

    public void accept(User reviewer, String notes, String resultingEntityType, UUID resultingEntityId) {
        this.reviewer = reviewer;
        this.reviewDecision = "ACCEPTED";
        this.reviewNotes = notes;
        this.reviewedAt = Instant.now();
        this.resultingEntityType = resultingEntityType;
        this.resultingEntityId = resultingEntityId;
    }

    public void reject(User reviewer, String notes) {
        this.reviewer = reviewer;
        this.reviewDecision = "REJECTED";
        this.reviewNotes = notes;
        this.reviewedAt = Instant.now();
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getPromptTemplateName() {
        return promptTemplateName;
    }

    public String getPromptTemplateVersion() {
        return promptTemplateVersion;
    }

    public String getPromptTemplateHash() {
        return promptTemplateHash;
    }

    public String getInputEntityIds() {
        return inputEntityIds;
    }

    public String getInputHashes() {
        return inputHashes;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public String getStructuredOutput() {
        return structuredOutput;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public BigDecimal getTotalCostUsd() {
        return totalCostUsd;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public User getReviewer() {
        return reviewer;
    }

    public String getReviewDecision() {
        return reviewDecision;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getResultingEntityType() {
        return resultingEntityType;
    }

    public UUID getResultingEntityId() {
        return resultingEntityId;
    }
}
