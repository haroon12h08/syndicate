-- Append-only audit trail (spec §38) and conflict-detection issues (spec §18).

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID REFERENCES transactions(id),
    actor_user_id UUID REFERENCES users(id),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID,
    summary VARCHAR(1024) NOT NULL,
    previous_value VARCHAR(1024),
    new_value VARCHAR(1024),
    reason VARCHAR(512),
    occurred_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_transaction_time ON audit_events (transaction_id, occurred_at DESC);
CREATE INDEX idx_audit_entity ON audit_events (entity_type, entity_id);

-- Conflicts surface as issues; the key makes re-detection idempotent instead of duplicating.
ALTER TABLE issues ADD COLUMN conflict_key VARCHAR(255);
CREATE INDEX idx_issues_conflict_key ON issues (conflict_key);
