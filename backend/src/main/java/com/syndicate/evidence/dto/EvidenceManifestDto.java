package com.syndicate.evidence.dto;

import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.candidatefact.CandidateFactStatus;
import com.syndicate.evidence.EvidenceDocumentType;
import com.syndicate.evidence.EvidenceQuality;
import com.syndicate.evidence.RetentionState;
import com.syndicate.fact.FactStatus;

import java.util.List;
import java.util.UUID;

/**
 * Manifest providing complete spatial grounding metadata for an evidence document:
 * its pages, all bounding box anchors (verified, pending, rejected), and linked facts.
 */
public record EvidenceManifestDto(
        UUID evidenceId,
        String fileName,
        String contentType,
        int pageCount,
        EvidenceQuality quality,
        RetentionState retentionState,
        String fileSha256,
        EvidenceDocumentType documentType,
        List<SpatialAnchorDto> anchors,
        List<LinkedFactDto> linkedFacts
) {
    public record SpatialAnchorDto(
            UUID candidateFactId,
            UUID resultingFactId,
            String factKey,
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
            CandidateFactStatus status,
            CandidateFactSource source,
            boolean isVerified
    ) {
    }

    public record LinkedFactDto(
            UUID id,
            String factKey,
            String label,
            String value,
            String unit,
            String period,
            FactStatus status,
            boolean hasSpatialAnchor,
            Integer pageNumber
    ) {
    }
}
