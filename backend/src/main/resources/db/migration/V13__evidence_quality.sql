-- Evidence quality (spec §7.3; plan A5). Distinct from fact verification: a fact can have
-- evidence attached and still be unverified, and good evidence does not verify a fact.
ALTER TABLE evidence
    ADD COLUMN quality VARCHAR(16) NOT NULL DEFAULT 'UNREVIEWED',
    ADD COLUMN quality_reason VARCHAR(512),
    ADD COLUMN quality_reviewed_by_user_id UUID REFERENCES users(id),
    ADD COLUMN quality_reviewed_at TIMESTAMP;

-- Versions that already have a successor are superseded as sources.
UPDATE evidence SET quality = 'SUPERSEDED' WHERE superseded_at IS NOT NULL;
