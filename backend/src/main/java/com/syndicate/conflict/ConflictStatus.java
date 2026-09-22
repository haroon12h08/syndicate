package com.syndicate.conflict;

public enum ConflictStatus {
    /** Competing values are live; nothing downstream may treat either as settled. */
    OPEN,
    /** A person chose a value and rejected the others. */
    RESOLVED,
    /** The disagreement disappeared on its own (a value was corrected or validity no longer overlaps). */
    CLEARED
}
