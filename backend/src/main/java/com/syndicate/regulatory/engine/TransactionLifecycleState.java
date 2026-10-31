package com.syndicate.regulatory.engine;

/**
 * 14-stage transaction state machine (spec §37-38; plan D6).
 * Replaces unstructured ad-hoc status updates with deterministic, precondition-guarded lifecycle transitions.
 */
public enum TransactionLifecycleState {
    DRAFT,
    ACTIVE,
    DUE_DILIGENCE,
    READINESS_REVIEW,
    DOCUMENT_PREPARATION,
    INTERNAL_APPROVAL,
    FILING_READY,
    FILED,
    OBSERVATIONS,
    RESPONSE,
    RESUBMISSION,
    ISSUE_PREPARATION,
    LISTED,
    CLOSED;

    public boolean isTerminal() {
        return this == LISTED || this == CLOSED;
    }
}
