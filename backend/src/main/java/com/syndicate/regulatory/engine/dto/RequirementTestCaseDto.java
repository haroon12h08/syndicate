package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.RequirementTestCase;

import java.time.Instant;
import java.util.UUID;

public record RequirementTestCaseDto(
        UUID id,
        UUID requirementVersionId,
        String name,
        String fixtureInputs,
        String expectedStatus,
        Instant lastRunAt,
        Boolean lastRunPassed
) {
    public static RequirementTestCaseDto from(RequirementTestCase tc) {
        return new RequirementTestCaseDto(
                tc.getId(),
                tc.getRequirementVersion().getId(),
                tc.getName(),
                tc.getFixtureInputs(),
                tc.getExpectedStatus(),
                tc.getLastRunAt(),
                tc.getLastRunPassed()
        );
    }
}
