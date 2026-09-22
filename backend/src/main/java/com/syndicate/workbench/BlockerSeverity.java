package com.syndicate.workbench;

/** How a blocker affects readiness, strongest first. */
public enum BlockerSeverity {
    /** Must be fixed before the transaction is ready at all. */
    BLOCKING,
    /** Derived output no longer matches the transaction state. */
    STALE,
    /** Incomplete, but the gap is known and bounded. */
    CONDITIONAL,
    /** Only sign-off remains. */
    AWAITING_REVIEW
}
