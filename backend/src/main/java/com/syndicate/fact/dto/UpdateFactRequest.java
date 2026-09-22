package com.syndicate.fact.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record UpdateFactRequest(
        String factKey,
        @NotBlank String label,
        @NotBlank String value,
        String unit,
        String period,
        Instant validFrom,
        Instant validTo,
        String reason
) {
}
