package com.syndicate.search.dto;

import java.time.Instant;
import java.util.UUID;

public record SearchResultDto(
        String entityType,
        UUID entityId,
        UUID workstreamId,
        String title,
        String subtitle,
        String snippet,
        String state,
        String provenanceSummary,
        String deepLinkPath,
        Double score,
        Instant createdAt
) {}
