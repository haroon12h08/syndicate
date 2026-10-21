package com.syndicate.ai.runs;

import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "extraction_runs")
public class ExtractionRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 32)
    private ExtractionRunType runType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExtractionRunStatus status;

    @Column(name = "candidates_count", nullable = false)
    private int candidatesCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_artifact_id")
    private AiArtifact aiArtifact;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ExtractionRun() {
    }

    public ExtractionRun(Evidence evidence, ExtractionRunType runType) {
        this.evidence = evidence;
        this.runType = runType;
        this.status = ExtractionRunStatus.RUNNING;
    }

    public void complete(int count, AiArtifact artifact, Long durationMs) {
        this.status = ExtractionRunStatus.COMPLETED;
        this.candidatesCount = count;
        this.aiArtifact = artifact;
        this.durationMs = durationMs;
        this.completedAt = Instant.now();
    }

    public void fail(String error, Long durationMs) {
        this.status = ExtractionRunStatus.FAILED;
        this.errorMessage = error;
        this.durationMs = durationMs;
        this.completedAt = Instant.now();
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public ExtractionRunType getRunType() {
        return runType;
    }

    public ExtractionRunStatus getStatus() {
        return status;
    }

    public int getCandidatesCount() {
        return candidatesCount;
    }

    public AiArtifact getAiArtifact() {
        return aiArtifact;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
