package com.syndicate.litigation.dto;

import com.syndicate.litigation.LitigationCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LitigationCaseDto(
        UUID id,
        UUID transactionId,
        UUID workstreamId,
        String caseNumber,
        String courtForum,
        String jurisdictionCity,
        String caseTitle,
        String category,
        String entityType,
        String entityName,
        String partyRole,
        String oppositeParty,
        LocalDate filingDate,
        BigDecimal claimedAmount,
        BigDecimal contingentLiabilityExposure,
        BigDecimal provisionMade,
        String potentialImpact,
        String caseSummary,
        String status,
        boolean isMaterial,
        String materialityBasis,
        List<UUID> evidenceIds
) {
    public static LitigationCaseDto fromEntity(LitigationCase lc, List<UUID> evidenceIds) {
        return new LitigationCaseDto(
                lc.getId(),
                lc.getTransaction().getId(),
                lc.getWorkstream().getId(),
                lc.getCaseNumber(),
                lc.getCourtForum(),
                lc.getJurisdictionCity(),
                lc.getCaseTitle(),
                lc.getCategory(),
                lc.getEntityType(),
                lc.getEntityName(),
                lc.getPartyRole(),
                lc.getOppositeParty(),
                lc.getFilingDate(),
                lc.getClaimedAmount(),
                lc.getContingentLiabilityExposure(),
                lc.getProvisionMade(),
                lc.getPotentialImpact(),
                lc.getCaseSummary(),
                lc.getStatus(),
                lc.isMaterial(),
                lc.getMaterialityBasis(),
                evidenceIds != null ? evidenceIds : List.of()
        );
    }
}
