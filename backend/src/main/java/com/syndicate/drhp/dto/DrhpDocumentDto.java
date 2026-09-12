package com.syndicate.drhp.dto;

import com.syndicate.drhp.DrhpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DrhpDocumentDto(
        UUID id,
        UUID transactionId,
        int version,
        DrhpStatus status,
        List<CompiledSection> sections,
        String lintSummary,
        String invalidatedReason,
        Instant compiledAt,
        String compiledByName,
        List<UUID> citedFactIds
) {
}
