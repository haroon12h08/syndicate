package com.syndicate.fact.dto;

import java.util.List;
import java.util.UUID;

/**
 * Everything needed to follow a printed DRHP value back to the page it came from: the fact, the
 * evidence it is linked to, and — when the value originated from a document extraction — the exact
 * page region a human accepted it from.
 */
public record FactTraceDto(
        FactDto fact,
        List<EvidenceRefDto> evidence,
        SpatialOriginDto origin
) {
    public record EvidenceRefDto(UUID id, String fileName, String documentType) {
    }

    public record SpatialOriginDto(
            UUID candidateFactId,
            UUID evidenceId,
            String evidenceFileName,
            int pageNumber,
            double bboxX,
            double bboxY,
            double bboxWidth,
            double bboxHeight,
            double pageImageWidth,
            double pageImageHeight,
            String source,
            String reviewedByName,
            java.time.Instant reviewedAt
    ) {
    }
}
