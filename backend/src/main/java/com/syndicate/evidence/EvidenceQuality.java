package com.syndicate.evidence;

import java.util.EnumSet;
import java.util.Set;

public enum EvidenceQuality {
    UNREVIEWED,
    ACCEPTABLE,
    INSUFFICIENT,
    CONFLICTING,
    STALE,
    SUPERSEDED,
    INVALID;

    /** States a reviewer may assign. SUPERSEDED is set by the system when a new version arrives. */
    public static final Set<EvidenceQuality> REVIEWER_ASSIGNABLE =
            EnumSet.of(ACCEPTABLE, INSUFFICIENT, CONFLICTING, STALE, INVALID);

    /** Evidence in these states cannot be the only support for a verified fact. */
    public static final Set<EvidenceQuality> UNSUPPORTIVE = EnumSet.of(INSUFFICIENT, INVALID);
}
