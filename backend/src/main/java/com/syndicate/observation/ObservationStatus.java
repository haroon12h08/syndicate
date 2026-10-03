package com.syndicate.observation;

public enum ObservationStatus {
    /** Received, not yet answered. */
    OPEN,
    /** A response is written but nobody else has read it. */
    RESPONSE_DRAFTED,
    /** The response has been approved and can go back to the authority. */
    RESPONSE_APPROVED,
    /** Recorded as sent, with the state it was sent against. */
    RESPONDED,
    /** The authority treated it as settled. */
    CLOSED
}
