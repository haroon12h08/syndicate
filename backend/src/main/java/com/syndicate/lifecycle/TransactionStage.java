package com.syndicate.lifecycle;

/**
 * Where a transaction stands, in the words a team would use. Derived from what the transaction
 * holds rather than kept in step by hand (spec §37-38); the two acts Syndicate cannot observe are
 * recorded as milestones.
 */
public enum TransactionStage {
    COLLECTING_DOCUMENTS,
    DILIGENCE,
    PREPARING_THE_DOCUMENT,
    AWAITING_APPROVAL,
    FILING_READY,
    FILED,
    ANSWERING_OBSERVATIONS,
    LISTED
}
