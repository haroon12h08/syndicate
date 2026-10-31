package com.syndicate.litigation.dto;

import java.math.BigDecimal;
import java.util.List;

public record LitigationSummaryDto(
        BigDecimal totalClaimedAmount,
        BigDecimal totalContingentLiability,
        int totalCasesCount,
        int materialCasesCount,
        int pendingCriminalCount,
        int pendingRegulatoryCount,
        List<LitigationCaseDto> cases,
        List<String> diligenceBlockers
) {}
