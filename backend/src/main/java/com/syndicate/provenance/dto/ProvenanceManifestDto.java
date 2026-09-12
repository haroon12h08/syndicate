package com.syndicate.provenance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProvenanceManifestDto(
        UUID manifestId,
        UUID drhpDocumentId,
        int documentVersion,
        String compileMode,
        String merkleRoot,
        String algorithm,
        int leafCount,
        Instant compiledAt,
        String compiledByName,
        List<ProvenanceLeafDto> leaves,
        boolean rootVerified
) {
}
