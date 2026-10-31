package com.syndicate.rpt.dto;

import com.syndicate.rpt.RelatedPartyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RelatedPartyTransactionDto(
        UUID id,
        UUID transactionId,
        UUID relatedPartyId,
        String relatedPartyName,
        String relationshipNature,
        String financialYear,
        String transactionType,
        BigDecimal amount,
        String currency,
        boolean inOrdinaryCourse,
        boolean isArmsLength,
        String armLengthBasis,
        String auditCommitteeApproval,
        LocalDate auditCommitteeApprovalDate,
        BigDecimal auditCommitteeMaxAmount,
        LocalDate boardApprovalDate,
        boolean shareholderApprovalRequired,
        LocalDate shareholderApprovalDate,
        boolean ca2013Sec188Applicable,
        boolean sebiLodrMaterial,
        List<UUID> evidenceIds
) {
    public static RelatedPartyTransactionDto fromEntity(RelatedPartyTransaction rpt, List<UUID> evidenceIds) {
        return new RelatedPartyTransactionDto(
                rpt.getId(),
                rpt.getTransaction().getId(),
                rpt.getRelatedParty().getId(),
                rpt.getRelatedParty().getName(),
                rpt.getRelatedParty().getRelationshipNature(),
                rpt.getFinancialYear(),
                rpt.getTransactionType(),
                rpt.getAmount(),
                rpt.getCurrency(),
                rpt.isInOrdinaryCourse(),
                rpt.isArmsLength(),
                rpt.getArmLengthBasis(),
                rpt.getAuditCommitteeApproval(),
                rpt.getAuditCommitteeApprovalDate(),
                rpt.getAuditCommitteeMaxAmount(),
                rpt.getBoardApprovalDate(),
                rpt.isShareholderApprovalRequired(),
                rpt.getShareholderApprovalDate(),
                rpt.isCa2013Sec188Applicable(),
                rpt.isSebiLodrMaterial(),
                evidenceIds != null ? evidenceIds : List.of()
        );
    }
}
