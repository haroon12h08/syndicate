package com.syndicate.contract.dto;

import java.math.BigDecimal;
import java.util.List;

public record ContractSummaryDto(
        int totalContractsCount,
        int above10PctCount,
        int changeOfControlPendingCount,
        int exclusivityCount,
        BigDecimal totalAnnualValue,
        List<MaterialContractDto> contracts,
        List<String> diligenceBlockers
) {}
