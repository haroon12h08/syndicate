package com.syndicate.fact.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFactRequest(
        @NotBlank String label,
        @NotBlank String value,
        String unit,
        String period
) {
}
