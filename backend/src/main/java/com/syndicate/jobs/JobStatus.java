package com.syndicate.jobs;

public enum JobStatus {
    PENDING,
    RUNNING,
    DONE,
    /** Retries exhausted. The row stays as the dead-letter record. */
    FAILED
}
