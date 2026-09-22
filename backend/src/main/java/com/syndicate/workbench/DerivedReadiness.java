package com.syndicate.workbench;

/** Spec §14. Derived from concrete blockers only; never a percentage. */
public enum DerivedReadiness {
    NOT_READY,
    CONDITIONALLY_READY,
    READY_FOR_REVIEW,
    READY_FOR_FILING,
    STALE_AFTER_CHANGE
}
