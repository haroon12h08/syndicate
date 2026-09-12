-- Disclosures, the fact dependency edges they are built from, and compiled DRHP documents.

CREATE TABLE disclosures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    section_code VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    -- Free text with {{fact:Label}} placeholders resolved at compile time.
    body_template TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    stale_reason VARCHAR(512),
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_disclosure_section UNIQUE (transaction_id, section_code)
);

-- Dependency DAG edge: this disclosure is derived from these facts.
CREATE TABLE disclosure_fact_link (
    disclosure_id UUID NOT NULL REFERENCES disclosures(id) ON DELETE CASCADE,
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (disclosure_id, fact_id)
);

CREATE TABLE drhp_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    -- Structured sections with text/fact segments, so every printed value stays traceable.
    compiled_body TEXT NOT NULL,
    lint_summary VARCHAR(2048),
    invalidated_reason VARCHAR(512),
    compiled_at TIMESTAMP NOT NULL,
    compiled_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE drhp_fact_link (
    drhp_document_id UUID NOT NULL REFERENCES drhp_documents(id) ON DELETE CASCADE,
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (drhp_document_id, fact_id)
);

CREATE INDEX idx_disclosures_transaction ON disclosures (transaction_id);
CREATE INDEX idx_drhp_transaction ON drhp_documents (transaction_id, version DESC);
CREATE INDEX idx_disclosure_fact_link_fact ON disclosure_fact_link (fact_id);
