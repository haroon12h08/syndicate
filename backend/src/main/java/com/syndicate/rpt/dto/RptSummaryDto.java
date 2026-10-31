package com.syndicate.rpt.dto;

import java.math.BigDecimal;
import java.util.List;

public record RptSummaryDto(
        BigDecimal totalRptExposure,
        int totalTransactionCount,
        int materialTransactionCount,
        int unapprovedTransactionCount,
        List<RelatedPartyDto> parties,
        List<RelatedPartyTransactionDto> transactions,
        List<String> complianceIssues
) {}
