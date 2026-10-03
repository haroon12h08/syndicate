-- Diligence questions as transaction state (spec §9; plan B4).
--
-- These are the questions a team already asks. Holding them as records, each with an owner, an
-- answer, the evidence behind it and a review, turns an implicit checklist into something the
-- transaction can be measured against - and something a later reader can audit.
CREATE TABLE diligence_question_library (
    code VARCHAR(64) PRIMARY KEY,
    question VARCHAR(1024) NOT NULL,
    workstream_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL DEFAULT 'SME_IPO',
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE diligence_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    code VARCHAR(64),
    question VARCHAR(1024) NOT NULL,
    workstream_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    answer VARCHAR(4000),
    not_applicable_reason VARCHAR(1024),
    owner_user_id UUID REFERENCES users(id),
    answered_by_user_id UUID REFERENCES users(id),
    answered_at TIMESTAMP,
    due_date DATE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (transaction_id, code)
);

CREATE TABLE diligence_question_facts (
    question_id UUID NOT NULL REFERENCES diligence_questions(id) ON DELETE CASCADE,
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (question_id, fact_id)
);

CREATE TABLE diligence_question_evidence (
    question_id UUID NOT NULL REFERENCES diligence_questions(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    PRIMARY KEY (question_id, evidence_id)
);

CREATE INDEX idx_diligence_questions_transaction ON diligence_questions (transaction_id, status);

INSERT INTO diligence_question_library (code, question, workstream_type, severity) VALUES
    ('FUNDRAISING_HISTORY', 'What equity issuances were made in the lookback period, and on what terms?', 'CAPITAL_STRUCTURE', 'BLOCKING'),
    ('FUNDRAISING_CONSIDERATION', 'What consideration was received for each issuance, and how was it received?', 'CAPITAL_STRUCTURE', 'BLOCKING'),
    ('FUND_FLOW', 'Where did the funds move after receipt?', 'FINANCIAL_DUE_DILIGENCE', 'BLOCKING'),
    ('PROMOTER_COUNTERPARTIES', 'Are any promoter or group entities counterparties to these transactions?', 'LEGAL_DUE_DILIGENCE', 'BLOCKING'),
    ('INVENTORY_MOVEMENT', 'Why did inventory change materially over the period under review?', 'FINANCIAL_DUE_DILIGENCE', 'HIGH'),
    ('RECEIVABLES_MOVEMENT', 'Why did receivables change materially, and what is the ageing?', 'FINANCIAL_DUE_DILIGENCE', 'HIGH'),
    ('EBITDA_MOVEMENT', 'Why did EBITDA change materially over the period under review?', 'FINANCIAL_DUE_DILIGENCE', 'HIGH'),
    ('LITIGATION_ABOVE_THRESHOLD', 'Are there pending litigations above the materiality threshold, against the company, its promoters or its directors?', 'LITIGATION', 'BLOCKING'),
    ('LICENCE_VALIDITY', 'Are all material licences valid through the relevant period, and what happens on expiry?', 'REGULATORY_DUE_DILIGENCE', 'BLOCKING'),
    ('CAPEX_VENDORS', 'Are the major capex vendors financially and operationally credible, and are they unrelated?', 'BUSINESS_DUE_DILIGENCE', 'HIGH'),
    ('OBJECTS_SUPPORT', 'What supports each stated object of the issue, and how were the amounts arrived at?', 'TRANSACTION_READINESS', 'BLOCKING'),
    ('SITE_VISIT', 'Has the required site visit taken place, and what did it establish?', 'BUSINESS_DUE_DILIGENCE', 'BLOCKING'),
    ('WORKING_CAPITAL', 'How was the working-capital requirement computed, and what supports the assumptions?', 'FINANCIAL_DUE_DILIGENCE', 'HIGH'),
    ('AUDITOR_CHANGES', 'Have there been auditor changes or qualifications in the period under review?', 'FINANCIAL_DUE_DILIGENCE', 'HIGH'),
    ('RELATED_PARTY_TRANSACTIONS', 'What related-party transactions exist, on what terms, and how were they approved?', 'LEGAL_DUE_DILIGENCE', 'BLOCKING'),
    ('NET_WORTH_LEVERAGE', 'How have net worth and leverage moved, and what explains the movement?', 'FINANCIAL_DUE_DILIGENCE', 'HIGH'),
    ('BANK_STATEMENTS', 'Do the bank statements reconcile with the reported financial statements?', 'FINANCIAL_DUE_DILIGENCE', 'BLOCKING'),
    ('VALUATION_BASIS', 'On what basis was the issue priced, and what supports it?', 'TRANSACTION_READINESS', 'HIGH');
