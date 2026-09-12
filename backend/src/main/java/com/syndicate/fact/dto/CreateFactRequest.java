package com.syndicate.fact.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record CreateFactRequest(
        @NotBlank String label,
        @NotBlank String value,
        String unit,
        String period,
        Instant validFrom,
        Instant validTo
) {
}
