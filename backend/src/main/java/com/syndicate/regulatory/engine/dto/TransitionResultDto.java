package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.TransactionLifecycleState;

import java.time.Instant;
import java.util.List;

public record TransitionResultDto(
        boolean allowed,
        TransactionLifecycleState fromState,
        TransactionLifecycleState toState,
        Instant transitionedAt,
        String reason,
        List<TransitionBlockerDto> blockers,
        List<RegulatoryExceptionDto> exceptionsApplied
) {}
