-- Approvals bound to a document version, and the filing package they authorise
-- (spec §4.8, §17, §35; plan E3, F1).
--
-- An approval states that a named person approved one exact compiled version. When that version
-- stops matching the transaction state, the approval is invalidated with the reason, so an old
-- sign-off can never appear to cover a new document.
CREATE TABLE document_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    drhp_document_id UUID NOT NULL REFERENCES drhp_documents(id),
    approver_user_id UUID NOT NULL REFERENCES users(id),
    approver_role VARCHAR(32) NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    invalidated_at TIMESTAMP,
    invalidation_reason VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (drhp_document_id, approver_role)
);

CREATE INDEX idx_document_approvals_document ON document_approvals (drhp_document_id);

-- Which roles must approve a compiled document before it can become a filing package.
CREATE TABLE document_approval_requirements (
    required_role VARCHAR(32) PRIMARY KEY
);
INSERT INTO document_approval_requirements (required_role) VALUES ('ISSUER_ADMIN'), ('LEAD_BANKER');

-- The package handed to the people who file. It is produced from one approved document version
-- and records its own hash, so what was handed over can be checked later.
CREATE TABLE filing_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    drhp_document_id UUID NOT NULL REFERENCES drhp_documents(id) UNIQUE,
    storage_path VARCHAR(512) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    merkle_root VARCHAR(64),
    built_by_user_id UUID NOT NULL REFERENCES users(id),
    built_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Approvals join the dependency graph, so a change upstream of the document reports them stale.
CREATE OR REPLACE VIEW dependency_edges AS
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
JOIN evidence e ON e.id = l.evidence_id
UNION ALL
SELECT r.transaction_id,
       'REVIEW', r.id, r.id,
       'FACT', f.lineage_id,
       f.id, (f.system_superseded_at IS NULL AND f.status <> 'REJECTED'),
       'REVIEWS', TRUE
FROM reviews r
JOIN facts f ON f.id = r.target_version_id
WHERE r.target_type = 'FACT'
UNION ALL
SELECT a.transaction_id,
       'APPROVAL', a.id, a.id,
       'DOCUMENT', doc.id,
       doc.id, (doc.status <> 'INVALIDATED'),
       'APPROVES', (a.invalidated_at IS NULL)
FROM document_approvals a
JOIN drhp_documents doc ON doc.id = a.drhp_document_id;
