package com.syndicate.ai.drafting.dto;

import java.util.List;
import java.util.UUID;

public record DraftObservationResponse(
        UUID observationId,
        String suggestedResponse,
        List<UUID> suggestedFactIds,
        List<UUID> suggestedEvidenceIds,
        List<UUID> suggestedDisclosureIds,
        String reasoning,
        UUID aiArtifactId
) {}
