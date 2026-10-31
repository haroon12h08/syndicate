package com.syndicate.regulatory.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateExceptionRequest(
        @NotNull UUID controlId,
        @NotBlank String reason,
        @NotBlank String affectedState,
        UUID supportingEvidenceId,
        String riskClassification,
        LocalDate expiryDate,
        @NotBlank String mitigationPlan
) {}
