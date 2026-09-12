-- Bi-temporal fact history.
--
-- Two independent time axes:
--   business time (valid_from / valid_to) - when the fact was true in the real world
--   system time   (created_at / system_superseded_at) - when Syndicate believed it
--
-- created_at already records the start of system time for each version, so only the
-- closing bound needs a new column.

ALTER TABLE facts
    ADD COLUMN valid_from TIMESTAMP,
    ADD COLUMN valid_to TIMESTAMP,
    ADD COLUMN system_superseded_at TIMESTAMP;

UPDATE facts SET valid_from = created_at WHERE valid_from IS NULL;

-- Existing superseded rows stopped being the system's belief when their successor was recorded.
UPDATE facts old
SET system_superseded_at = new_version.created_at
FROM facts new_version
WHERE new_version.supersedes_fact_id = old.id
  AND old.system_superseded_at IS NULL;

ALTER TABLE facts ALTER COLUMN valid_from SET NOT NULL;

CREATE INDEX idx_facts_supersedes_fact_id ON facts (supersedes_fact_id);
CREATE INDEX idx_facts_workstream_system_time ON facts (workstream_id, created_at, system_superseded_at);
