-- Deterministic SEBI rules registry and per-transaction readiness evaluations.

ALTER TABLE transactions ADD COLUMN estimated_filing_date DATE;

CREATE TABLE regulatory_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(128) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1024) NOT NULL,
    workstream_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    fact_label_pattern VARCHAR(255) NOT NULL,
    minimum_facts INTEGER NOT NULL DEFAULT 1,
    expression TEXT NOT NULL,
    requirement_summary VARCHAR(512) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE rule_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    rule_id UUID NOT NULL REFERENCES regulatory_rules(id),
    status VARCHAR(32) NOT NULL,
    actual_value VARCHAR(512),
    detail VARCHAR(1024),
    evaluated_at TIMESTAMP NOT NULL,
    generated_issue_id UUID REFERENCES issues(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_rule_evaluation_per_transaction UNIQUE (transaction_id, rule_id)
);

CREATE TABLE rule_evaluation_fact_link (
    rule_evaluation_id UUID NOT NULL REFERENCES rule_evaluations(id) ON DELETE CASCADE,
    fact_id UUID NOT NULL REFERENCES facts(id),
    PRIMARY KEY (rule_evaluation_id, fact_id)
);

CREATE INDEX idx_rule_evaluations_transaction ON rule_evaluations (transaction_id);

-- Issues raised by the engine are tracked so re-evaluation updates them instead of duplicating.
ALTER TABLE issues ADD COLUMN generated_by_rule_id UUID REFERENCES regulatory_rules(id);
CREATE INDEX idx_issues_generated_by_rule ON issues (generated_by_rule_id);

INSERT INTO regulatory_rules
    (code, title, description, workstream_type, severity, fact_label_pattern, minimum_facts,
     expression, requirement_summary)
VALUES
    ('SEBI_SME_POST_ISSUE_CAPITAL',
     'Post-issue paid-up capital ceiling',
     'SEBI ICDR requires the post-issue paid-up face value capital of an SME issuer to not exceed INR 25 Crore.',
     'CAPITAL_STRUCTURE',
     'BLOCKING',
     'Post-Issue Paid-Up Capital',
     1,
     '{"<=": [{"factValue": "Post-Issue Paid-Up Capital"}, 250000000]}',
     'Must not exceed INR 25,00,00,000 (INR 25 Crore)'),

    ('SEBI_SME_EBITDA_TRACK_RECORD',
     'Operating profit track record',
     'The issuer must have positive EBITDA / operating profit in at least 2 of the 3 preceding financial years.',
     'FINANCIAL_DUE_DILIGENCE',
     'BLOCKING',
     'EBITDA',
     3,
     '{">=": [{"countFactsAbove": ["EBITDA", 0]}, 2]}',
     'Positive EBITDA in at least 2 of the 3 preceding financial years'),

    ('SEBI_SME_VALID_LICENSES',
     'Critical licences valid at filing',
     'All critical operating and manufacturing licences must remain valid beyond the estimated filing date.',
     'REGULATORY_DUE_DILIGENCE',
     'BLOCKING',
     'License',
     1,
     '{">": [{"minValidTo": "License"}, {"var": "estimatedFilingDate"}]}',
     'Every critical licence must stay valid past the estimated filing date');
