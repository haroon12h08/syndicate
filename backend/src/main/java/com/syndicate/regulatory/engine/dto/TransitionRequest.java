package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.TransactionLifecycleState;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(
        @NotNull TransactionLifecycleState targetState,
        String reason,
        boolean forceWithException
) {}
