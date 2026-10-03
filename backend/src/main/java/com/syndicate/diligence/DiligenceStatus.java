package com.syndicate.diligence;

public enum DiligenceStatus {
    /** Nobody has answered it yet. */
    OPEN,
    /** Answered, with whatever evidence and facts the answer rests on. */
    ANSWERED,
    /** Answered and accepted by someone other than the person who answered. */
    ACCEPTED,
    /** Deliberately out of scope for this transaction, with a reason. */
    NOT_APPLICABLE
}
