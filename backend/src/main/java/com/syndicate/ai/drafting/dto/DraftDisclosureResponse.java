package com.syndicate.ai.drafting.dto;

import java.util.List;
import java.util.UUID;

public record DraftDisclosureResponse(
        String sectionCode,
        String suggestedTitle,
        String bodyTemplate,
        List<String> citedFactKeys,
        UUID aiArtifactId
) {}
