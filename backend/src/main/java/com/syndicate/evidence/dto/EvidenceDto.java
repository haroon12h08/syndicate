package com.syndicate.evidence.dto;

import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceDocumentType;
import com.syndicate.evidence.ProcessingStatus;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record EvidenceDto(
        UUID id,
        UUID workstreamId,
        String fileName,
        String contentType,
        long fileSizeBytes,
        EvidenceDocumentType documentType,
        UserDto uploadedBy,
        Instant uploadedAt,
        String fileSha256,
        ProcessingStatus processingStatus,
        String processingError
) {
    public static EvidenceDto from(Evidence evidence) {
        return new EvidenceDto(
                evidence.getId(),
                evidence.getWorkstream().getId(),
                evidence.getFileName(),
                evidence.getContentType(),
                evidence.getFileSizeBytes(),
                evidence.getDocumentType(),
                UserDto.from(evidence.getUploadedByUser()),
                evidence.getUploadedAt(),
                evidence.getFileSha256(),
                evidence.getProcessingStatus(),
                evidence.getProcessingError()
        );
    }
}
