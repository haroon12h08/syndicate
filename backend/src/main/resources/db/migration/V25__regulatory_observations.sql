-- Observations from an exchange or regulator, and the response to them (spec §18, §37; plan F2-F3).
--
-- A query about a filing is not an email thread. It is raised against a transaction, it points at
-- facts, documents and disclosures, it is answered by a named person, and the answer is read by
-- someone else before it goes back. All of that is kept.
CREATE TABLE regulatory_observations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    authority VARCHAR(64) NOT NULL,
    reference VARCHAR(128),
    received_date DATE NOT NULL,
    observation VARCHAR(4000) NOT NULL,
    section_code VARCHAR(64),
    severity VARCHAR(16) NOT NULL DEFAULT 'HIGH',
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    response_deadline DATE,
    owner_user_id UUID REFERENCES users(id),
    response VARCHAR(8000),
    responded_by_user_id UUID REFERENCES users(id),
    responded_at TIMESTAMP,
    approved_by_user_id UUID REFERENCES users(id),
    approved_at TIMESTAMP,
    drhp_document_id UUID REFERENCES drhp_documents(id),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE observation_facts (
    observation_id UUID NOT NULL REFERENCES regulatory_observations(id) ON DELETE CASCADE,
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (observation_id, fact_id)
);

CREATE TABLE observation_evidence (
    observation_id UUID NOT NULL REFERENCES regulatory_observations(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    PRIMARY KEY (observation_id, evidence_id)
);

CREATE TABLE observation_disclosures (
    observation_id UUID NOT NULL REFERENCES regulatory_observations(id) ON DELETE CASCADE,
    disclosure_id UUID NOT NULL REFERENCES disclosures(id),
    PRIMARY KEY (observation_id, disclosure_id)
);

CREATE INDEX idx_observations_transaction ON regulatory_observations (transaction_id, status);
