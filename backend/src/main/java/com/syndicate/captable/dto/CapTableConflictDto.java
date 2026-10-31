package com.syndicate.captable.dto;

import java.math.BigDecimal;

public record CapTableConflictDto(
        String factKey,
        String description,
        BigDecimal expectedValue,
        BigDecimal actualFactValue,
        String status
) {}
