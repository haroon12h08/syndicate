package com.syndicate.captable.dto;

import com.syndicate.captable.SecurityClass;
import com.syndicate.captable.SecurityType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SecurityClassDto(
        UUID id,
        UUID transactionId,
        String name,
        SecurityType securityType,
        String isin,
        String currency,
        BigDecimal faceValue,
        BigDecimal votingRightsPerShare,
        BigDecimal authorizedShares,
        boolean convertible,
        BigDecimal conversionRatio,
        BigDecimal conversionPrice,
        LocalDate conversionDeadline
) {
    public static SecurityClassDto fromEntity(SecurityClass sc) {
        return new SecurityClassDto(
                sc.getId(),
                sc.getTransaction().getId(),
                sc.getName(),
                sc.getSecurityType(),
                sc.getIsin(),
                sc.getCurrency(),
                sc.getFaceValue(),
                sc.getVotingRightsPerShare(),
                sc.getAuthorizedShares(),
                sc.isConvertible(),
                sc.getConversionRatio(),
                sc.getConversionPrice(),
                sc.getConversionDeadline()
        );
    }
}
