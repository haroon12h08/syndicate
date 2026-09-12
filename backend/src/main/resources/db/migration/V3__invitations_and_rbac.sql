CREATE TABLE organization_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    inviter_user_id UUID NOT NULL REFERENCES users(id),
    invitee_email VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP,
    responded_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE transaction_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    inviter_user_id UUID NOT NULL REFERENCES users(id),
    invitee_email VARCHAR(255),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    workstream_id UUID REFERENCES workstreams(id),
    role VARCHAR(32) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP,
    responded_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE transaction_approval_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    transition VARCHAR(64) NOT NULL,
    required_role VARCHAR(32) NOT NULL,
    signed_by_user_id UUID NOT NULL REFERENCES users(id),
    signed_at TIMESTAMP NOT NULL DEFAULT now(),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_approval_signature UNIQUE (transaction_id, transition, required_role)
);

CREATE INDEX idx_org_invitations_email ON organization_invitations(invitee_email);
CREATE INDEX idx_org_invitations_org ON organization_invitations(organization_id);
CREATE INDEX idx_txn_invitations_email ON transaction_invitations(invitee_email);
CREATE INDEX idx_txn_invitations_txn ON transaction_invitations(transaction_id);
CREATE INDEX idx_approval_signatures_txn ON transaction_approval_signatures(transaction_id);
