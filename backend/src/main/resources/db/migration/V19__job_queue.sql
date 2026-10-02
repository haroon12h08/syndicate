-- Background work queue (replaces the external message broker).
--
-- A job is enqueued in the same database transaction as the change that needs it, so work can
-- never be lost between committing the change and telling a broker about it. Workers claim rows
-- with SELECT ... FOR UPDATE SKIP LOCKED, which makes several workers safe without coordination.
-- Exhausted retries leave the row FAILED: that is the dead-letter queue.
CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(64) NOT NULL,
    target_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    run_after TIMESTAMP NOT NULL DEFAULT now(),
    claimed_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- One live job per target and type: re-queueing an evidence that is already waiting is a no-op.
CREATE UNIQUE INDEX uq_jobs_live ON jobs (type, target_id) WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_jobs_claimable ON jobs (status, run_after);
