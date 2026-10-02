-- Rules address facts by semantic key, and carry their provenance (spec §13, §57; plan D1).
--
-- Matching on display labels made a rule's outcome depend on how someone typed a fact's name.
-- Rules now name the fact key they test, which is stable and catalogued.
--
-- Every rule also records where it comes from. The rules shipped with this system have not been
-- verified against an authoritative source by a qualified reviewer, so they are marked
-- INTERNAL_POLICY: the product must present them as configured checks, never as regulation.
ALTER TABLE regulatory_rules
    ADD COLUMN fact_key VARCHAR(128),
    ADD COLUMN source_authority VARCHAR(64),
    ADD COLUMN source_citation VARCHAR(512),
    ADD COLUMN source_url VARCHAR(1024),
    ADD COLUMN effective_from DATE,
    ADD COLUMN effective_to DATE,
    ADD COLUMN version INT NOT NULL DEFAULT 1,
    ADD COLUMN provenance_status VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_POLICY';

-- The capital ceiling needs a fact that is not in the catalogue yet.
INSERT INTO fact_definitions (fact_key, display_label, value_type, is_financial, default_materiality)
VALUES ('issue.post_issue_paid_up_capital', 'Post-issue paid-up capital', 'MONEY', FALSE, 'CRITICAL');
INSERT INTO fact_definition_aliases (alias, fact_key) VALUES
    ('post-issue paid-up capital', 'issue.post_issue_paid_up_capital'),
    ('post issue paid up capital', 'issue.post_issue_paid_up_capital');

UPDATE regulatory_rules SET fact_key = 'issue.post_issue_paid_up_capital',
    expression = '{"<=": [{"factValue": "issue.post_issue_paid_up_capital"}, 250000000]}'
WHERE code = 'SEBI_SME_POST_ISSUE_CAPITAL';

UPDATE regulatory_rules SET fact_key = 'issuer.ebitda',
    expression = '{">=": [{"countFactsAbove": ["issuer.ebitda", 0]}, 2]}'
WHERE code = 'SEBI_SME_EBITDA_TRACK_RECORD';

UPDATE regulatory_rules SET fact_key = 'issuer.licence_expiry',
    expression = '{">": [{"minValidTo": "issuer.licence_expiry"}, {"var": "estimatedFilingDate"}]}'
WHERE code = 'SEBI_SME_VALID_LICENSES';

-- These three were written from secondary material, so they are configured checks, not regulation.
UPDATE regulatory_rules SET provenance_status = 'INTERNAL_POLICY', source_authority = 'UNVERIFIED',
    source_citation = 'Derived from public summaries of SEBI ICDR SME requirements; not verified '
        || 'against the authoritative text by qualified counsel.'
WHERE fact_key IS NOT NULL;

ALTER TABLE regulatory_rules ALTER COLUMN fact_key SET NOT NULL;
ALTER TABLE regulatory_rules DROP COLUMN fact_label_pattern;

-- A rule that has not been evaluated against this transaction's data states nothing about it.
COMMENT ON COLUMN regulatory_rules.provenance_status IS
    'SOURCED = cited to an authoritative text and reviewed; INTERNAL_POLICY = a configured check only.';
