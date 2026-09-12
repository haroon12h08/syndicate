package com.syndicate.regulatory.dto;

import com.syndicate.regulatory.ReadinessState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReadinessDto(
        ReadinessState state,
        int rulesTotal,
        int rulesPassed,
        int rulesFailed,
        int rulesMissingEvidence,
        LocalDate estimatedFilingDate,
        Instant lastEvaluatedAt,
        List<RuleEvaluationDto> rules,
        List<BlockingIssueDto> blockingIssues
) {
}
