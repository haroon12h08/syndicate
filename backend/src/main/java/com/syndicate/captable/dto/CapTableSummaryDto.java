package com.syndicate.captable.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CapTableSummaryDto(
        LocalDate asOfDate,
        BigDecimal totalBasicShares,
        BigDecimal totalDilutedShares,
        BigDecimal preIssuePaidUpCapitalInr,
        BigDecimal freshIssueShares,
        BigDecimal ofsShares,
        BigDecimal postIssueShares,
        BigDecimal postIssuePaidUpCapitalInr,
        BigDecimal promoterBasicShares,
        BigDecimal promoterBasicPct,
        BigDecimal promoterPostIssueShares,
        BigDecimal promoterPostIssuePct,
        BigDecimal mpcRequiredShares,
        BigDecimal mpcShortfallShares,
        boolean isMpcSatisfied,
        List<HolderShareholdingDto> holdings,
        List<ShareLockInDto> lockIns,
        List<ShareEncumbranceDto> encumbrances,
        List<ShareTransactionDto> transactions,
        IpoIssueParametersDto ipoParameters,
        List<CapTableConflictDto> conflicts
) {}
