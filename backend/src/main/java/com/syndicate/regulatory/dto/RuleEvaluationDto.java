package com.syndicate.regulatory.dto;

import com.syndicate.issue.IssueSeverity;
import com.syndicate.regulatory.RuleEvaluation;
import com.syndicate.regulatory.RuleEvaluationStatus;
import com.syndicate.workstream.WorkstreamType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuleEvaluationDto(
        UUID id,
        String ruleCode,
        String ruleTitle,
        String ruleDescription,
        String requirementSummary,
        WorkstreamType workstreamType,
        IssueSeverity severity,
        RuleEvaluationStatus status,
        String actualValue,
        String detail,
        Instant evaluatedAt,
        List<UUID> supportingFactIds,
        UUID generatedIssueId,
        UUID generatedIssueWorkstreamId
) {
    public static RuleEvaluationDto from(RuleEvaluation evaluation) {
        return new RuleEvaluationDto(
                evaluation.getId(),
                evaluation.getRule().getCode(),
                evaluation.getRule().getTitle(),
                evaluation.getRule().getDescription(),
                evaluation.getRule().getRequirementSummary(),
                evaluation.getRule().getWorkstreamType(),
                evaluation.getRule().getSeverity(),
                evaluation.getStatus(),
                evaluation.getActualValue(),
                evaluation.getDetail(),
                evaluation.getEvaluatedAt(),
                evaluation.getSupportingFacts().stream().map(f -> f.getId()).toList(),
                evaluation.getGeneratedIssue() != null ? evaluation.getGeneratedIssue().getId() : null,
                evaluation.getGeneratedIssue() != null
                        ? evaluation.getGeneratedIssue().getWorkstream().getId() : null
        );
    }
}
