package com.syndicate.review.dto;

import com.syndicate.fact.Materiality;
import com.syndicate.transaction.TransactionRole;

import java.util.UUID;

/** A current material fact that still lacks a current approving review from a required role. */
public record MissingReviewDto(UUID factId, UUID lineageId, String factKey, String label, Materiality materiality,
                               TransactionRole requiredRole) {
}
