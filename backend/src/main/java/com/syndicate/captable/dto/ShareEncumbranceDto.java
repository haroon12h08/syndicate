package com.syndicate.captable.dto;

import com.syndicate.captable.EncumbranceType;
import com.syndicate.captable.ShareEncumbrance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ShareEncumbranceDto(
        UUID id,
        UUID transactionId,
        UUID holderId,
        String holderName,
        UUID securityClassId,
        String securityClassName,
        BigDecimal encumberedShares,
        EncumbranceType encumbranceType,
        String pledgeeName,
        LocalDate pledgeDate,
        LocalDate releaseDate,
        String status,
        UUID evidenceId
) {
    public static ShareEncumbranceDto fromEntity(ShareEncumbrance se) {
        return new ShareEncumbranceDto(
                se.getId(),
                se.getTransaction().getId(),
                se.getHolder().getId(),
                se.getHolder().getLegalName(),
                se.getSecurityClass().getId(),
                se.getSecurityClass().getName(),
                se.getEncumberedShares(),
                se.getEncumbranceType(),
                se.getPledgeeName(),
                se.getPledgeDate(),
                se.getReleaseDate(),
                se.getStatus(),
                se.getEvidence() != null ? se.getEvidence().getId() : null
        );
    }
}
