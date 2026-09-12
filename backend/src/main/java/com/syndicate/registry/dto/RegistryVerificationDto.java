package com.syndicate.registry.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RegistryVerificationDto(
        UUID companyId,
        String status,
        String source,
        String lookupKey,
        boolean found,
        String payloadSha256,
        Instant fetchedAt,
        int fieldsCompared,
        int mismatchCount,
        List<FieldComparisonDto> comparisons,
        List<UUID> raisedIssueIds,
        String message
) {
    public record FieldComparisonDto(
            String field,
            String internalValue,
            String registryValue,
            boolean match,
            String internalSource,
            UUID issueId,
            UUID issueWorkstreamId
    ) {
    }
}
