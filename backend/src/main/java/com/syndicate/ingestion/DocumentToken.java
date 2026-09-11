package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;

public record DocumentToken(
        String text,
        int pageNumber,
        double x,
        double y,
        double width,
        double height,
        int pageImageWidth,
        int pageImageHeight,
        CandidateFactSource source
) {
}
