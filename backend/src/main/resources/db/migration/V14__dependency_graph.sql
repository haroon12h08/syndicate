-- Transaction dependency graph (spec §5, §6; plan §2.3, A7).
--
-- One uniform edge shape over every link table, so traversal and staleness are defined once.
-- It is a view rather than a table so it can never drift from the links it describes; each new
-- link table (reviews, approvals, claims, ...) is added to the UNION by a later migration.
--
--   from_*  the dependent node        to_*  the node it depends on
--   pinned_version_id  the exact version of the dependency the dependent was built from
--   pinned_is_current  false once that dependency has a newer version -> the edge is stale
--   from_is_current    false for dependents that are themselves superseded or invalidated
CREATE VIEW dependency_edges AS
SELECT d.transaction_id,
       'DISCLOSURE' AS from_type, d.id AS from_id, d.id AS from_lineage_id,
       'FACT' AS to_type, f.lineage_id AS to_lineage_id,
       f.id AS pinned_version_id, (f.system_superseded_at IS NULL) AS pinned_is_current,
       'USES_VALUE' AS kind, TRUE AS from_is_current
FROM disclosure_fact_link l
JOIN disclosures d ON d.id = l.disclosure_id
JOIN facts f ON f.id = l.fact_id
UNION ALL
SELECT doc.transaction_id,
       'DOCUMENT', doc.id, doc.id,
       'FACT', f.lineage_id,
       f.id, (f.system_superseded_at IS NULL),
       'USES_VALUE', (doc.status <> 'INVALIDATED')
FROM drhp_fact_link l
JOIN drhp_documents doc ON doc.id = l.drhp_document_id
JOIN facts f ON f.id = l.fact_id
UNION ALL
SELECT w.transaction_id,
       'FACT', f.id, f.lineage_id,
       'EVIDENCE', e.lineage_id,
       e.id, (e.superseded_at IS NULL),
       'SUPPORTED_BY', (f.system_superseded_at IS NULL)
FROM fact_evidence_link l
JOIN facts f ON f.id = l.fact_id
JOIN workstreams w ON w.id = f.workstream_id
JOIN evidence e ON e.id = l.evidence_id;
