-- Evidence custody (spec §4.1, §7.1, §53; plan A4).
--
-- Evidence is never deleted or overwritten. A corrected source is a new version in the same
-- lineage; withdrawing a document archives it, and archived evidence stays readable so every
-- fact and filing that relied on it remains traceable.
ALTER TABLE evidence
    ADD COLUMN lineage_id UUID,
    ADD COLUMN version INT NOT NULL DEFAULT 1,
    ADD COLUMN parent_evidence_id UUID REFERENCES evidence(id),
    ADD COLUMN superseded_at TIMESTAMP,
    ADD COLUMN retention_state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN archived_at TIMESTAMP,
    ADD COLUMN archived_by_user_id UUID REFERENCES users(id),
    ADD COLUMN archive_reason VARCHAR(512),
    ADD COLUMN access_classification VARCHAR(16) NOT NULL DEFAULT 'CONFIDENTIAL',
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'UPLOAD';

UPDATE evidence SET lineage_id = id WHERE lineage_id IS NULL;
ALTER TABLE evidence ALTER COLUMN lineage_id SET NOT NULL;

CREATE UNIQUE INDEX uq_evidence_lineage_version ON evidence (lineage_id, version);
CREATE INDEX idx_evidence_sha256 ON evidence (file_sha256);
