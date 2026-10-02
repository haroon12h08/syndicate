package com.syndicate.onboarding;

import jakarta.validation.constraints.NotBlank;

/**
 * Everything the product genuinely needs to open a deal. Anything else it can decide for itself,
 * and anything it cannot decide can be answered later, in context.
 */
public record StartTransactionRequest(@NotBlank String companyName, String cin) {
}
