-- Audit hardening (spec §33, invariant 15).
--
-- The audit trail must outlive the objects it describes, including a deleted transaction, so the
-- transaction foreign key is dropped: transaction_id becomes a plain reference.
ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS audit_events_transaction_id_fkey;

-- chain_seq gives a total insertion order; hash/prev_hash form a per-transaction SHA-256 chain.
-- Rows written before this migration have no hash and are reported as legacy (unchained).
ALTER TABLE audit_events
    ADD COLUMN chain_seq BIGSERIAL,
    ADD COLUMN correlation_id VARCHAR(64),
    ADD COLUMN prev_hash VARCHAR(64),
    ADD COLUMN hash VARCHAR(64);

CREATE INDEX idx_audit_chain ON audit_events (transaction_id, chain_seq);

CREATE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only (% rejected)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();

CREATE TRIGGER audit_events_no_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_append_only();
