package com.syndicate.observation.dto;

import com.syndicate.drhp.Disclosure;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.observation.ObservationStatus;
import com.syndicate.observation.RegulatoryObservation;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ObservationDto(UUID id, String authority, String reference, LocalDate receivedDate, String observation,
                             String sectionCode, IssueSeverity severity, ObservationStatus status,
                             LocalDate responseDeadline, UserDto owner, String response, UserDto respondedBy,
                             Instant respondedAt, UserDto approvedBy, Instant approvedAt, UUID documentId,
                             Integer documentVersion, List<UUID> relatedFactIds, List<UUID> relatedEvidenceIds,
                             List<UUID> relatedDisclosureIds) {

    public static ObservationDto from(RegulatoryObservation o) {
        return new ObservationDto(o.getId(), o.getAuthority(), o.getReference(), o.getReceivedDate(),
                o.getObservation(), o.getSectionCode(), o.getSeverity(), o.getStatus(), o.getResponseDeadline(),
                o.getOwner() != null ? UserDto.from(o.getOwner()) : null,
                o.getResponse(),
                o.getRespondedBy() != null ? UserDto.from(o.getRespondedBy()) : null, o.getRespondedAt(),
                o.getApprovedBy() != null ? UserDto.from(o.getApprovedBy()) : null, o.getApprovedAt(),
                o.getDocument() != null ? o.getDocument().getId() : null,
                o.getDocument() != null ? o.getDocument().getVersion() : null,
                o.getRelatedFacts().stream().map(Fact::getId).sorted().toList(),
                o.getRelatedEvidence().stream().map(Evidence::getId).sorted().toList(),
                o.getRelatedDisclosures().stream().map(Disclosure::getId).sorted().toList());
    }
}
