package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.ControlEvaluation;

import java.time.Instant;
import java.util.UUID;

public record ControlEvaluationDto(
        UUID id,
        UUID transactionId,
        UUID controlId,
        String controlCode,
        String controlName,
        String requirementCode,
        String citation,
        String sourceUrl,
        boolean isUnsourced,
        String severity,
        String status,
        String inputsSnapshot,
        String expressionEvaluated,
        String resultValue,
        String explanation,
        UUID exceptionId,
        String exceptionReason,
        Instant evaluatedAt
) {
    public static ControlEvaluationDto from(ControlEvaluation eval) {
        return new ControlEvaluationDto(
                eval.getId(),
                eval.getTransaction().getId(),
                eval.getControl().getId(),
                eval.getControl().getCode(),
                eval.getControl().getName(),
                eval.getControl().getRequirementVersion().getRequirement().getCode(),
                eval.getControl().getRequirementVersion().getCitation(),
                eval.getControl().getRequirementVersion().getSourceUrl(),
                eval.getControl().getRequirementVersion().isUnsourced(),
                eval.getControl().getSeverity(),
                eval.getStatus(),
                eval.getInputsSnapshot(),
                eval.getExpressionEvaluated(),
                eval.getResultValue(),
                eval.getExplanation(),
                eval.getException() != null ? eval.getException().getId() : null,
                eval.getException() != null ? eval.getException().getReason() : null,
                eval.getEvaluatedAt()
        );
    }
}
