package com.syndicate.ai.suggestion;

import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record AiSuggestionDto(
        UUID id,
        UUID transactionId,
        UUID workstreamId,
        UUID aiArtifactId,
        SuggestionType suggestionType,
        String title,
        String description,
        SuggestionSeverity severity,
        SuggestionStatus status,
        String targetType,
        UUID targetId,
        String actionType,
        String actionPayload,
        String dismissReason,
        UserDto reviewedBy,
        Instant reviewedAt,
        Instant appliedAt,
        Instant createdAt
) {
    public static AiSuggestionDto from(AiSuggestion s) {
        return new AiSuggestionDto(
                s.getId(),
                s.getTransaction().getId(),
                s.getWorkstream() != null ? s.getWorkstream().getId() : null,
                s.getAiArtifact() != null ? s.getAiArtifact().getId() : null,
                s.getSuggestionType(),
                s.getTitle(),
                s.getDescription(),
                s.getSeverity(),
                s.getStatus(),
                s.getTargetType(),
                s.getTargetId(),
                s.getActionType(),
                s.getActionPayload(),
                s.getDismissReason(),
                s.getReviewedByUser() != null ? UserDto.from(s.getReviewedByUser()) : null,
                s.getReviewedAt(),
                s.getAppliedAt(),
                s.getCreatedAt()
        );
    }
}
