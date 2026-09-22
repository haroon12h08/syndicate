package com.syndicate.fact;

/** Spec §10. Separate from confidence and evidence quality: a certain fact can still be critical. */
public enum Materiality {
    LOW,
    NORMAL,
    MATERIAL,
    CRITICAL;

    public boolean requiresIndependentVerification() {
        return this == MATERIAL || this == CRITICAL;
    }
}
