-- V27: AI artifacts provenance and extraction runs (Phase G: G1 & G2, spec §26-28).

CREATE TABLE ai_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    model_version VARCHAR(64),
    prompt_template_name VARCHAR(128) NOT NULL,
    prompt_template_version VARCHAR(64) NOT NULL,
    prompt_template_hash VARCHAR(64) NOT NULL,
    input_entity_ids JSONB NOT NULL DEFAULT '[]',
    input_hashes JSONB NOT NULL DEFAULT '{}',
    raw_output TEXT NOT NULL,
    structured_output JSONB,
    confidence DOUBLE PRECISION,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_cost_usd NUMERIC(10, 6),
    duration_ms BIGINT,
    reviewer_id UUID REFERENCES users(id),
    review_decision VARCHAR(32),
    review_notes TEXT,
    reviewed_at TIMESTAMP,
    resulting_entity_type VARCHAR(64),
    resulting_entity_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_artifacts_created_at ON ai_artifacts(created_at);
CREATE INDEX idx_ai_artifacts_reviewer ON ai_artifacts(reviewer_id);
CREATE INDEX idx_ai_artifacts_decision ON ai_artifacts(review_decision);
CREATE INDEX idx_ai_artifacts_resulting ON ai_artifacts(resulting_entity_type, resulting_entity_id);

CREATE TABLE extraction_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id UUID NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
    run_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    candidates_count INTEGER NOT NULL DEFAULT 0,
    ai_artifact_id UUID REFERENCES ai_artifacts(id),
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_extraction_runs_evidence ON extraction_runs(evidence_id);
CREATE INDEX idx_extraction_runs_status ON extraction_runs(status);

ALTER TABLE candidate_facts ADD COLUMN ai_artifact_id UUID REFERENCES ai_artifacts(id);
ALTER TABLE candidate_facts ADD COLUMN confidence DOUBLE PRECISION;
CREATE INDEX idx_candidate_facts_ai_artifact ON candidate_facts(ai_artifact_id);

ALTER TABLE evidence ADD COLUMN suggested_document_type VARCHAR(64);
ALTER TABLE evidence ADD COLUMN classification_ai_artifact_id UUID REFERENCES ai_artifacts(id);
CREATE INDEX idx_evidence_classification_artifact ON evidence(classification_ai_artifact_id);
