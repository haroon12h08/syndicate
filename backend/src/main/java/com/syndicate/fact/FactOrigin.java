package com.syndicate.fact;

/** How a fact entered the transaction. Machine output only ever arrives as CANDIDATE, via human acceptance. */
public enum FactOrigin {
    MANUAL,
    CANDIDATE,
    CALCULATED,
    REGISTRY
}
