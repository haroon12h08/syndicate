package com.syndicate.conflict.dto;

import com.syndicate.conflict.ConflictResolution;
import com.syndicate.conflict.ConflictStatus;
import com.syndicate.conflict.FactConflict;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A conflict with every competing source laid out for adjudication (spec §65). */
public record ConflictDto(UUID id, UUID transactionId, String factKey, String period, ConflictStatus status,
                          UUID issueId, Instant detectedAt, Instant closedAt, List<MemberDto> members,
                          List<ResolutionDto> resolutions) {

    public record SourceDto(UUID evidenceId, String fileName, String documentType, UserDto uploadedBy,
                            Instant uploadedAt, String quality) {
        static SourceDto from(Evidence e) {
            return new SourceDto(e.getId(), e.getFileName(), e.getDocumentType().name(),
                    UserDto.from(e.getUploadedByUser()), e.getUploadedAt(), e.getQuality().name());
        }
    }

    public record MemberDto(UUID factId, UUID lineageId, String label, String value, String unit, String status,
                            String materiality, Instant validFrom, Instant validTo, UserDto createdBy,
                            UserDto verifiedBy, List<SourceDto> sources) {
        static MemberDto from(Fact f) {
            return new MemberDto(f.getId(), f.getLineageId(), f.getLabel(), f.getValue(), f.getUnit(),
                    f.getStatus().name(), f.getMateriality().name(), f.getValidFrom(), f.getValidTo(),
                    UserDto.from(f.getCreatedByUser()),
                    f.getVerifiedByUser() != null ? UserDto.from(f.getVerifiedByUser()) : null,
                    f.getEvidence().stream().map(SourceDto::from)
                            .sorted(Comparator.comparing(SourceDto::uploadedAt)).toList());
        }
    }

    public record ResolutionDto(UUID id, UUID chosenFactId, List<UUID> rejectedFactIds, List<UUID> evidenceConsideredIds,
                                String reason, UserDto resolvedBy, Instant resolvedAt) {
        static ResolutionDto from(ConflictResolution r) {
            return new ResolutionDto(r.getId(), r.getChosenFact().getId(),
                    r.getRejectedFacts().stream().map(Fact::getId).sorted().toList(),
                    r.getEvidenceConsidered().stream().map(Evidence::getId).sorted().toList(),
                    r.getReason(), UserDto.from(r.getResolvedBy()), r.getResolvedAt());
        }
    }

    public static ConflictDto from(FactConflict c) {
        return new ConflictDto(c.getId(), c.getTransactionId(), c.getFactKey(), c.getPeriod(), c.getStatus(),
                c.getIssue() != null ? c.getIssue().getId() : null, c.getDetectedAt(), c.getClosedAt(),
                c.getMembers().stream().map(MemberDto::from)
                        .sorted(Comparator.comparing(MemberDto::validFrom)).toList(),
                c.getResolutions().stream().map(ResolutionDto::from).toList());
    }
}
