package com.syndicate.review.dto;

import com.syndicate.review.ReviewDecision;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateReviewRequest(@NotNull ReviewDecision decision, String comments, List<UUID> evidenceConsideredIds) {
}
