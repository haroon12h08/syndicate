-- Version-bound reviews (spec §4.7, §17; plan B2).
--
-- A review attaches to one concrete version of its target. It is never edited: when the target
-- gets a new version the review simply stops being current (see dependency_edges), so a review
-- of v1 can never appear to cover v2.
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    target_type VARCHAR(32) NOT NULL,
    target_version_id UUID NOT NULL,
    target_lineage_id UUID NOT NULL,
    reviewer_user_id UUID NOT NULL REFERENCES users(id),
    reviewer_role VARCHAR(32) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    comments VARCHAR(4000),
    reviewed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE review_evidence (
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    PRIMARY KEY (review_id, evidence_id)
);

CREATE INDEX idx_reviews_target ON reviews (target_type, target_lineage_id);
CREATE INDEX idx_reviews_transaction ON reviews (transaction_id);

-- Which roles must sign off a fact of a given materiality before it is considered reviewed.
CREATE TABLE review_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type VARCHAR(32) NOT NULL,
    materiality VARCHAR(16) NOT NULL,
    required_role VARCHAR(32) NOT NULL,
    UNIQUE (target_type, materiality, required_role)
);

INSERT INTO review_requirements (target_type, materiality, required_role) VALUES
    ('FACT', 'MATERIAL', 'DUE_DILIGENCE_TEAM'),
    ('FACT', 'CRITICAL', 'DUE_DILIGENCE_TEAM'),
    ('FACT', 'CRITICAL', 'LEAD_BANKER');

-- Reviews join the dependency graph: a review depends on the version it reviewed.
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
WHERE r.target_type = 'FACT';
