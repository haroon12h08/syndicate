package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;

public record MatchedCandidate(
        String label,
        String value,
        String period,
        int pageNumber,
        double bboxX,
        double bboxY,
        double bboxWidth,
        double bboxHeight,
        int pageImageWidth,
        int pageImageHeight,
        CandidateFactSource source
) {
}
