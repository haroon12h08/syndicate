package com.syndicate.ai.artifact.dto;

import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.user.UserDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiArtifactDto(
        UUID id,
        String provider,
        String model,
        String modelVersion,
        String promptTemplateName,
        String promptTemplateVersion,
        String promptTemplateHash,
        String inputEntityIds,
        String inputHashes,
        String rawOutput,
        String structuredOutput,
        Double confidence,
        Integer promptTokens,
        Integer completionTokens,
        BigDecimal totalCostUsd,
        Long durationMs,
        UserDto reviewer,
        String reviewDecision,
        String reviewNotes,
        Instant reviewedAt,
        String resultingEntityType,
        UUID resultingEntityId,
        Instant createdAt
) {
    public static AiArtifactDto from(AiArtifact a) {
        return new AiArtifactDto(
                a.getId(),
                a.getProvider(),
                a.getModel(),
                a.getModelVersion(),
                a.getPromptTemplateName(),
                a.getPromptTemplateVersion(),
                a.getPromptTemplateHash(),
                a.getInputEntityIds(),
                a.getInputHashes(),
                a.getRawOutput(),
                a.getStructuredOutput(),
                a.getConfidence(),
                a.getPromptTokens(),
                a.getCompletionTokens(),
                a.getTotalCostUsd(),
                a.getDurationMs(),
                a.getReviewer() != null ? UserDto.from(a.getReviewer()) : null,
                a.getReviewDecision(),
                a.getReviewNotes(),
                a.getReviewedAt(),
                a.getResultingEntityType(),
                a.getResultingEntityId(),
                a.getCreatedAt()
        );
    }
}
