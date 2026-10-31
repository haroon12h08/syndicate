package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.RegulatorySnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegulatorySnapshotDto(
        UUID id,
        UUID transactionId,
        String name,
        LocalDate effectiveDate,
        Instant pinnedAt,
        UUID pinnedById,
        String pinnedByName,
        String notes,
        boolean isActive,
        int itemsCount,
        List<RequirementVersionDto> items
) {
    public static RegulatorySnapshotDto from(RegulatorySnapshot s, List<RequirementVersionDto> items) {
        return new RegulatorySnapshotDto(
                s.getId(),
                s.getTransaction().getId(),
                s.getName(),
                s.getEffectiveDate(),
                s.getPinnedAt(),
                s.getPinnedBy() != null ? s.getPinnedBy().getId() : null,
                s.getPinnedBy() != null ? s.getPinnedBy().getFullName() : null,
                s.getNotes(),
                s.isActive(),
                items.size(),
                items
        );
    }
}
