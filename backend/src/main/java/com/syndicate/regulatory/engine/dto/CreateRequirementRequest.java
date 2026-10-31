package com.syndicate.regulatory.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateRequirementRequest(
        @NotBlank String code,
        @NotBlank String authority,
        String jurisdiction,
        @NotBlank String title,
        @NotBlank String description,
        String transactionType,
        String issueType,
        String severity,
        @NotBlank String citation,
        String sourceUrl,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String remediationGuide,
        String ruleExpression,
        List<CreateControlRequest> controls,
        List<CreateTestCaseRequest> testCases
) {
    public record CreateControlRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String description,
            @NotBlank String controlType,
            String expression,
            String severity,
            int orderIndex
    ) {}

    public record CreateTestCaseRequest(
            @NotBlank String name,
            @NotBlank String fixtureInputs,
            @NotBlank String expectedStatus
    ) {}
}
