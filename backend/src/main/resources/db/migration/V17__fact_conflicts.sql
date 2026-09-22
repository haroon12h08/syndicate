-- Structured conflicts (spec §11, §65, workflow D; plan B6).
--
-- A conflict is two or more current facts with the same key and period whose business validity
-- overlaps but whose values differ. Non-overlapping validity is succession, not conflict. Only a
-- person resolves a conflict; the losing facts are REJECTED, never deleted, and every
-- resolution is kept even if the conflict later reopens.
CREATE TABLE fact_conflicts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    conflict_key VARCHAR(512) NOT NULL UNIQUE,
    fact_key VARCHAR(128) NOT NULL,
    period VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    issue_id UUID REFERENCES issues(id),
    detected_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE fact_conflict_members (
    conflict_id UUID NOT NULL REFERENCES fact_conflicts(id) ON DELETE CASCADE,
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (conflict_id, fact_id)
);

CREATE TABLE fact_conflict_resolutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conflict_id UUID NOT NULL REFERENCES fact_conflicts(id) ON DELETE CASCADE,
    chosen_fact_id UUID NOT NULL REFERENCES facts(id),
    reason VARCHAR(2000) NOT NULL,
    resolved_by_user_id UUID NOT NULL REFERENCES users(id),
    resolved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE fact_conflict_resolution_rejected (
    resolution_id UUID NOT NULL REFERENCES fact_conflict_resolutions(id) ON DELETE CASCADE,
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (resolution_id, fact_id)
);

CREATE TABLE fact_conflict_resolution_evidence (
    resolution_id UUID NOT NULL REFERENCES fact_conflict_resolutions(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    PRIMARY KEY (resolution_id, evidence_id)
);

CREATE INDEX idx_fact_conflicts_transaction ON fact_conflicts (transaction_id, status);
CREATE INDEX idx_fact_conflict_members_fact ON fact_conflict_members (fact_id);
