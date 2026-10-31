package com.syndicate.captable.dto;

import com.syndicate.captable.IpoIssueParameters;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IpoIssueParametersDto(
        UUID transactionId,
        BigDecimal freshIssueShares,
        BigDecimal ofsShares,
        BigDecimal priceBandLower,
        BigDecimal priceBandUpper,
        LocalDate expectedAllotmentDate,
        BigDecimal totalIssueShares
) {
    public static IpoIssueParametersDto fromEntity(IpoIssueParameters ip) {
        if (ip == null) {
            return new IpoIssueParametersDto(null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO);
        }
        return new IpoIssueParametersDto(
                ip.getTransactionId(),
                ip.getFreshIssueShares(),
                ip.getOfsShares(),
                ip.getPriceBandLower(),
                ip.getPriceBandUpper(),
                ip.getExpectedAllotmentDate(),
                ip.getTotalIssueShares()
        );
    }
}
