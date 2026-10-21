package com.syndicate.ai.llm;

import java.math.BigDecimal;

public record LlmResponse(
        String rawText,
        int promptTokens,
        int completionTokens,
        BigDecimal totalCostUsd,
        long durationMs,
        String provider,
        String model,
        String modelVersion
) {
}
