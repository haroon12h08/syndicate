package com.syndicate.review.dto;

import com.syndicate.evidence.Evidence;
import com.syndicate.review.Review;
import com.syndicate.review.ReviewDecision;
import com.syndicate.review.ReviewTargetType;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code current} is false once the reviewed version has been superseded or rejected: the review
 * still records what was looked at, but it no longer covers the object as it stands.
 */
public record ReviewDto(UUID id, ReviewTargetType targetType, UUID targetVersionId, UUID targetLineageId,
                        UserDto reviewer, TransactionRole reviewerRole, ReviewDecision decision, String comments,
                        Instant reviewedAt, List<UUID> evidenceConsideredIds, boolean current) {

    public static ReviewDto from(Review r, boolean current) {
        return new ReviewDto(r.getId(), r.getTargetType(), r.getTargetVersionId(), r.getTargetLineageId(),
                UserDto.from(r.getReviewer()), r.getReviewerRole(), r.getDecision(), r.getComments(),
                r.getReviewedAt(), r.getEvidenceConsidered().stream().map(Evidence::getId).sorted().toList(), current);
    }
}
