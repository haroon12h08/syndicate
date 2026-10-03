-- The two things only a person can tell the system (spec §36, §37; plan D6).
--
-- Where a transaction stands is derived from what it holds: documents, verified facts, answered
-- questions, a compiled and approved filing package. Nobody should have to keep a status field in
-- step with reality. What cannot be derived is what happened outside Syndicate - the filing
-- itself, and the listing - so those are recorded as acts, with their reference and date.
CREATE TABLE transaction_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    type VARCHAR(32) NOT NULL,
    occurred_on DATE NOT NULL,
    reference VARCHAR(255),
    notes VARCHAR(1024),
    drhp_document_id UUID REFERENCES drhp_documents(id),
    recorded_by_user_id UUID NOT NULL REFERENCES users(id),
    recorded_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (transaction_id, type)
);
