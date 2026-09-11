package com.syndicate.candidatefact.dto;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.candidatefact.CandidateFactStatus;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record CandidateFactDto(
        UUID id,
        UUID workstreamId,
        UUID evidenceId,
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
        CandidateFactSource source,
        CandidateFactStatus status,
        String reviewNote,
        UserDto reviewedBy,
        Instant reviewedAt,
        UUID resultingFactId
) {
    public static CandidateFactDto from(CandidateFact c) {
        return new CandidateFactDto(
                c.getId(),
                c.getWorkstream().getId(),
                c.getEvidence().getId(),
                c.getLabel(),
                c.getValue(),
                c.getPeriod(),
                c.getPageNumber(),
                c.getBboxX(),
                c.getBboxY(),
                c.getBboxWidth(),
                c.getBboxHeight(),
                c.getPageImageWidth(),
                c.getPageImageHeight(),
                c.getSource(),
                c.getStatus(),
                c.getReviewNote(),
                c.getReviewedByUser() != null ? UserDto.from(c.getReviewedByUser()) : null,
                c.getReviewedAt(),
                c.getResultingFact() != null ? c.getResultingFact().getId() : null
        );
    }
}
