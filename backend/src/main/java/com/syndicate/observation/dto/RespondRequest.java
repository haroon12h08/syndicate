package com.syndicate.observation.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record RespondRequest(@NotBlank String response, List<UUID> factIds, List<UUID> evidenceIds,
                             List<UUID> disclosureIds) {
}
