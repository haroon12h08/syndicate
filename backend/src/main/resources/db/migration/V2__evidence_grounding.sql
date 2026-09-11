ALTER TABLE evidence ADD COLUMN file_sha256 VARCHAR(64);
ALTER TABLE evidence ADD COLUMN processing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';
ALTER TABLE evidence ADD COLUMN processing_error TEXT;

CREATE TABLE candidate_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workstream_id UUID NOT NULL REFERENCES workstreams(id),
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    label VARCHAR(255) NOT NULL,
    value VARCHAR(1024) NOT NULL,
    period VARCHAR(64),
    page_number INTEGER NOT NULL,
    bbox_x DOUBLE PRECISION NOT NULL,
    bbox_y DOUBLE PRECISION NOT NULL,
    bbox_width DOUBLE PRECISION NOT NULL,
    bbox_height DOUBLE PRECISION NOT NULL,
    page_image_width INTEGER NOT NULL,
    page_image_height INTEGER NOT NULL,
    source VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    review_note TEXT,
    reviewed_by_user_id UUID REFERENCES users(id),
    reviewed_at TIMESTAMP,
    resulting_fact_id UUID REFERENCES facts(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_candidate_facts_workstream ON candidate_facts(workstream_id);
CREATE INDEX idx_candidate_facts_evidence ON candidate_facts(evidence_id);
CREATE INDEX idx_candidate_facts_status ON candidate_facts(workstream_id, status);
