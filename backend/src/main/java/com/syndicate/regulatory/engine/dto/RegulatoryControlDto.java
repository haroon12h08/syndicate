package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.RegulatoryControl;

import java.util.UUID;

public record RegulatoryControlDto(
        UUID id,
        UUID requirementVersionId,
        String requirementCode,
        String citation,
        String sourceUrl,
        boolean isUnsourced,
        String code,
        String name,
        String description,
        String controlType,
        String expression,
        String severity,
        int orderIndex
) {
    public static RegulatoryControlDto from(RegulatoryControl ctrl) {
        return new RegulatoryControlDto(
                ctrl.getId(),
                ctrl.getRequirementVersion().getId(),
                ctrl.getRequirementVersion().getRequirement().getCode(),
                ctrl.getRequirementVersion().getCitation(),
                ctrl.getRequirementVersion().getSourceUrl(),
                ctrl.getRequirementVersion().isUnsourced(),
                ctrl.getCode(),
                ctrl.getName(),
                ctrl.getDescription(),
                ctrl.getControlType(),
                ctrl.getExpression(),
                ctrl.getSeverity(),
                ctrl.getOrderIndex()
        );
    }
}
