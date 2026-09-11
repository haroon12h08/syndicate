CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_membership UNIQUE (organization_id, user_id)
);

CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name VARCHAR(255) NOT NULL,
    cin VARCHAR(21),
    pan VARCHAR(10),
    registered_office TEXT,
    incorporation_date DATE,
    constitution VARCHAR(32),
    owner_organization_id UUID NOT NULL REFERENCES organizations(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_cin UNIQUE (cin)
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    lead_organization_id UUID NOT NULL REFERENCES organizations(id),
    type VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE transaction_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction_membership UNIQUE (transaction_id, user_id)
);

CREATE TABLE workstreams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    type VARCHAR(64) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workstream_id UUID NOT NULL REFERENCES workstreams(id),
    label VARCHAR(255) NOT NULL,
    value VARCHAR(1024) NOT NULL,
    unit VARCHAR(64),
    period VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    supersedes_fact_id UUID REFERENCES facts(id),
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    verified_by_user_id UUID REFERENCES users(id),
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workstream_id UUID NOT NULL REFERENCES workstreams(id),
    file_name VARCHAR(512) NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workstream_id UUID NOT NULL REFERENCES workstreams(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_user_id UUID REFERENCES users(id),
    due_date DATE,
    resolution TEXT,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE fact_evidence_link (
    fact_id UUID NOT NULL REFERENCES facts(id),
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    PRIMARY KEY (fact_id, evidence_id)
);

CREATE TABLE issue_fact_link (
    issue_id UUID NOT NULL REFERENCES issues(id),
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (issue_id, fact_id)
);

CREATE TABLE issue_evidence_link (
    issue_id UUID NOT NULL REFERENCES issues(id),
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    PRIMARY KEY (issue_id, evidence_id)
);

CREATE INDEX idx_org_membership_user ON organization_memberships(user_id);
CREATE INDEX idx_companies_owner_org ON companies(owner_organization_id);
CREATE INDEX idx_transactions_company ON transactions(company_id);
CREATE INDEX idx_transaction_membership_user ON transaction_memberships(user_id);
CREATE INDEX idx_transaction_membership_transaction ON transaction_memberships(transaction_id);
CREATE INDEX idx_workstreams_transaction ON workstreams(transaction_id);
CREATE INDEX idx_facts_workstream ON facts(workstream_id);
CREATE INDEX idx_evidence_workstream ON evidence(workstream_id);
CREATE INDEX idx_issues_workstream ON issues(workstream_id);
