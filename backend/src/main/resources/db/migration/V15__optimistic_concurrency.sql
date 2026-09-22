-- Concurrency control (spec §46; plan A8).
--
-- Two corrections racing on the same fact must not both produce "version 2": the second loses
-- on this constraint. row_version backs JPA optimistic locking for in-place state changes
-- (verification, staleness, invalidation), which are otherwise last-writer-wins. Evidence is
-- deliberately excluded: the extraction worker updates processing columns concurrently with
-- custody actions, which are guarded by explicit state checks instead.
CREATE UNIQUE INDEX uq_facts_lineage_version ON facts (lineage_id, version);
DROP INDEX IF EXISTS idx_facts_lineage;

ALTER TABLE facts ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE disclosures ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE drhp_documents ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
