package com.syndicate.regulatory.engine.dto;

import com.syndicate.regulatory.engine.TransactionLifecycleTransition;

import java.time.Instant;
import java.util.UUID;

public record TransactionLifecycleTransitionDto(
        UUID id,
        UUID transactionId,
        String fromState,
        String toState,
        UUID actorId,
        String actorName,
        String reason,
        String blockersSnapshot,
        String exceptionsSnapshot,
        Instant transitionedAt
) {
    public static TransactionLifecycleTransitionDto from(TransactionLifecycleTransition t) {
        return new TransactionLifecycleTransitionDto(
                t.getId(),
                t.getTransaction().getId(),
                t.getFromState(),
                t.getToState(),
                t.getActor().getId(),
                t.getActor().getFullName(),
                t.getReason(),
                t.getBlockersSnapshot(),
                t.getExceptionsSnapshot(),
                t.getTransitionedAt()
        );
    }
}
