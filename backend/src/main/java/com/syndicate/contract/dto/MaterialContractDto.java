package com.syndicate.contract.dto;

import com.syndicate.contract.MaterialContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MaterialContractDto(
        UUID id,
        UUID transactionId,
        UUID workstreamId,
        String contractReference,
        String title,
        String contractCategory,
        String counterpartyName,
        boolean isRelatedParty,
        UUID relatedPartyId,
        LocalDate executionDate,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        boolean isPerpetual,
        BigDecimal contractValueAnnual,
        BigDecimal contractValueTotal,
        BigDecimal revenuePercentage,
        BigDecimal supplyPercentage,
        boolean isAbove10PctScreeningIndicator,
        boolean hasChangeOfControl,
        String changeOfControlSummary,
        boolean changeOfControlConsentObtained,
        boolean hasExclusivity,
        String status,
        String reviewStatus,
        List<UUID> evidenceIds
) {
    public static MaterialContractDto fromEntity(MaterialContract mc, List<UUID> evidenceIds) {
        return new MaterialContractDto(
                mc.getId(),
                mc.getTransaction().getId(),
                mc.getWorkstream().getId(),
                mc.getContractReference(),
                mc.getTitle(),
                mc.getContractCategory(),
                mc.getCounterpartyName(),
                mc.isRelatedParty(),
                mc.getRelatedPartyEntity() != null ? mc.getRelatedPartyEntity().getId() : null,
                mc.getExecutionDate(),
                mc.getEffectiveDate(),
                mc.getExpiryDate(),
                mc.isPerpetual(),
                mc.getContractValueAnnual(),
                mc.getContractValueTotal(),
                mc.getRevenuePercentage(),
                mc.getSupplyPercentage(),
                mc.isAbove10PctScreeningIndicator(),
                mc.isHasChangeOfControl(),
                mc.getChangeOfControlSummary(),
                mc.isChangeOfControlConsentObtained(),
                mc.isHasExclusivity(),
                mc.getStatus(),
                mc.getReviewStatus(),
                evidenceIds != null ? evidenceIds : List.of()
        );
    }
}
