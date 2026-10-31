package com.syndicate.captable.dto;

import com.syndicate.captable.HolderCategory;
import java.math.BigDecimal;
import java.util.UUID;

public record HolderShareholdingDto(
        UUID holderId,
        String holderName,
        HolderCategory category,
        BigDecimal basicShares,
        BigDecimal basicOwnershipPct,
        BigDecimal dilutedShares,
        BigDecimal dilutedOwnershipPct,
        BigDecimal postIssueShares,
        BigDecimal postIssueOwnershipPct,
        BigDecimal lockedShares,
        BigDecimal encumberedShares
) {}
