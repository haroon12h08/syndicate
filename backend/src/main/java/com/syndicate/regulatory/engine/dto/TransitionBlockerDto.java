package com.syndicate.regulatory.engine.dto;

import java.util.UUID;

public record TransitionBlockerDto(
        String code,
        String title,
        String description,
        String severity,
        String category, // CONTROL, CONFLICT, WORKBENCH, APPROVAL
        UUID targetId,
        boolean canBeOverriddenWithException,
        boolean exceptionApplied
) {}
