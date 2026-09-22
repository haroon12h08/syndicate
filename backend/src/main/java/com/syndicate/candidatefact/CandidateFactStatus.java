package com.syndicate.candidatefact;

public enum CandidateFactStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    /** Its source document was replaced by a newer version before anyone reviewed it. */
    SUPERSEDED
}
