package com.syndicate.observation.dto;

import com.syndicate.issue.IssueSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecordObservationRequest(@NotBlank String authority, String reference,
                                       @NotNull LocalDate receivedDate, @NotBlank String observation,
                                       String sectionCode, IssueSeverity severity, LocalDate responseDeadline,
                                       UUID ownerUserId, List<UUID> factIds, List<UUID> evidenceIds,
                                       List<UUID> disclosureIds) {
}
