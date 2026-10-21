package com.syndicate.ai.artifact.dto;

import java.math.BigDecimal;

public record AiProvenanceMetricsDto(
        long totalCandidates,
        long aiCandidates,
        long acceptedAiCandidates,
        long rejectedAiCandidates,
        long pendingAiCandidates,
        double acceptanceRate,
        BigDecimal totalAiCostUsd,
        double averageReviewDurationSeconds
) {
}
