-- External registry snapshots (§49) and cryptographic filing provenance (§26, §60).

CREATE TABLE registry_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    source VARCHAR(64) NOT NULL,
    lookup_key VARCHAR(128) NOT NULL,
    legal_name VARCHAR(255),
    incorporation_date DATE,
    authorized_capital NUMERIC(20, 2),
    paid_up_capital NUMERIC(20, 2),
    registered_office VARCHAR(512),
    company_status VARCHAR(64),
    -- SHA-256 over the canonical snapshot, so a stored snapshot cannot be altered unnoticed.
    payload_sha256 VARCHAR(64) NOT NULL,
    raw_payload TEXT NOT NULL,
    fetched_at TIMESTAMP NOT NULL,
    fetched_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_registry_snapshots_company ON registry_snapshots (company_id, fetched_at DESC);

ALTER TABLE drhp_documents ADD COLUMN compile_mode VARCHAR(32) NOT NULL DEFAULT 'DRAFT_PREVIEW';
ALTER TABLE drhp_documents ADD COLUMN merkle_root VARCHAR(64);

CREATE TABLE filing_provenance_manifests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drhp_document_id UUID NOT NULL UNIQUE REFERENCES drhp_documents(id) ON DELETE CASCADE,
    merkle_root VARCHAR(64) NOT NULL,
    leaf_count INTEGER NOT NULL,
    -- Canonical leaf list; the root is recomputable from this alone.
    leaves TEXT NOT NULL,
    algorithm VARCHAR(32) NOT NULL DEFAULT 'SHA-256',
    compiled_at TIMESTAMP NOT NULL,
    compiled_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_provenance_root ON filing_provenance_manifests (merkle_root);
