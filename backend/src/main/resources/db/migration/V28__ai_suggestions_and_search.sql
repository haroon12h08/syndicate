-- V28: AI Suggestions Engine & Transaction Search (Phase G: G3, G4, G5, spec §26-28)

-- 1. Full-text search and extracted text column on evidence
ALTER TABLE evidence ADD COLUMN extracted_text TEXT;

-- GIN full-text search indexes for high-speed transaction search
CREATE INDEX idx_evidence_extracted_text_tsv ON evidence USING gin(to_tsvector('english', coalesce(extracted_text, '')));
CREATE INDEX idx_disclosures_body_tsv ON disclosures USING gin(to_tsvector('english', coalesce(body_template, '')));

-- 2. AI Suggestions Engine (Spec §26, §70, Build Plan §4 G4)
CREATE TABLE ai_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    workstream_id UUID REFERENCES workstreams(id) ON DELETE SET NULL,
    ai_artifact_id UUID REFERENCES ai_artifacts(id) ON DELETE SET NULL,
    suggestion_type VARCHAR(64) NOT NULL, -- PRE_CONFLICT, MISSING_EVIDENCE, DILIGENCE_GAP, RELATED_PARTY_ALERT
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'INFO', -- INFO, WARNING, BLOCKING
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, DISMISSED
    target_type VARCHAR(64) NOT NULL, -- CANDIDATE_FACT, WORKSTREAM, DILIGENCE_QUESTION, DISCLOSURE, FACT
    target_id UUID NOT NULL,
    action_type VARCHAR(64) NOT NULL, -- REJECT_CANDIDATE, LINK_EVIDENCE, RESOLVE_GAP, UPLOAD_CHECKLIST
    action_payload JSONB NOT NULL DEFAULT '{}',
    dismiss_reason TEXT,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_suggestions_tx_status ON ai_suggestions(transaction_id, status);
CREATE INDEX idx_ai_suggestions_target ON ai_suggestions(target_type, target_id);
CREATE INDEX idx_ai_suggestions_artifact ON ai_suggestions(ai_artifact_id);
