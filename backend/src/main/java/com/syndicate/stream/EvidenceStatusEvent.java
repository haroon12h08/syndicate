package com.syndicate.stream;

import com.syndicate.evidence.ProcessingStatus;

import java.util.UUID;

/** Published whenever a document's extraction state changes, so watchers can be told live. */
public record EvidenceStatusEvent(
        UUID transactionId,
        UUID evidenceId,
        String fileName,
        ProcessingStatus status,
        String error,
        int candidateFactCount
) {
}
