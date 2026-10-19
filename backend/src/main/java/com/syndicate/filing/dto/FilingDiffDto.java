package com.syndicate.filing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FilingDiffDto(
        UUID transactionId,
        DocumentRefDto baseDocument,
        DocumentRefDto targetDocument,
        PackageRefDto basePackage,
        PackageRefDto targetPackage,
        DiffStatsDto stats,
        List<FactDiffDto> facts,
        List<DisclosureDiffDto> disclosures,
        List<ObservationDiffDto> addressedObservations
) {
    public record DocumentRefDto(
            UUID id,
            int version,
            String compileMode,
            String merkleRoot,
            Instant compiledAt,
            String compiledBy
    ) {}

    public record PackageRefDto(
            UUID id,
            String fileName,
            String sha256,
            long sizeBytes,
            Instant builtAt
    ) {}

    public record DiffStatsDto(
            int factsSuperseded,
            int factsAdded,
            int factsRemoved,
            int factsUnchanged,
            int disclosuresModified,
            int disclosuresAdded,
            int disclosuresRemoved,
            int disclosuresUnchanged,
            int observationsAddressed
    ) {}

    public record FactDiffDto(
            UUID lineageId,
            String factKey,
            String label,
            String period,
            String unit,
            String changeType, // SUPERSEDED_VALUE_CHANGED, ADDED, REMOVED, UNCHANGED
            String baseValue,
            String targetValue,
            Integer baseVersion,
            Integer targetVersion,
            String changeReason,
            String correctedBy,
            Instant correctedAt,
            String verifiedBy,
            Instant verifiedAt,
            List<String> baseEvidenceFiles,
            List<String> targetEvidenceFiles
    ) {}

    public record DisclosureDiffDto(
            String sectionCode,
            String title,
            String changeType, // MODIFIED, ADDED, REMOVED, UNCHANGED
            String baseProse,
            String targetProse,
            List<String> affectedFactKeys
    ) {}

    public record ObservationDiffDto(
            UUID id,
            String authority,
            String reference,
            String sectionCode,
            String observation,
            String status,
            String responseDraft,
            Instant responseApprovedAt
    ) {}
}
