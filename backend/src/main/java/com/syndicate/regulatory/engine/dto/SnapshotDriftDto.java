package com.syndicate.regulatory.engine.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SnapshotDriftDto(
        boolean hasDrift,
        UUID pinnedSnapshotId,
        LocalDate pinnedEffectiveDate,
        int outdatedCount,
        List<OutdatedRequirementDto> outdatedRequirements
) {
    public record OutdatedRequirementDto(
            String requirementCode,
            String title,
            int pinnedVersionNumber,
            int latestVersionNumber,
            LocalDate latestEffectiveFrom,
            String summaryOfChanges
    ) {}
}
