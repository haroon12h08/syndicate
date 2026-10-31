-- V32: Phase D — Regulatory Engine, Rule Governance, Snapshots, Exceptions, and State Machine (spec §13, §37, §38, §39, §57; plan D1-D6)

-- 1. Extend transactions with explicit lifecycle_state
ALTER TABLE transactions
    ADD COLUMN lifecycle_state VARCHAR(64) NOT NULL DEFAULT 'DRAFT';

UPDATE transactions
SET lifecycle_state = CASE
    WHEN status = 'CLOSED' THEN 'CLOSED'
    WHEN status = 'ACTIVE' THEN 'DUE_DILIGENCE'
    ELSE 'DRAFT'
END;

-- 2. Requirements & Versioning (D1)
CREATE TABLE requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(128) NOT NULL UNIQUE,
    authority VARCHAR(64) NOT NULL,
    jurisdiction VARCHAR(32) NOT NULL DEFAULT 'INDIA',
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    transaction_type VARCHAR(64) NOT NULL DEFAULT 'ALL',
    issue_type VARCHAR(64) NOT NULL DEFAULT 'ALL',
    severity VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE requirement_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requirement_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    citation VARCHAR(512) NOT NULL,
    source_url VARCHAR(1024),
    source_document_hash VARCHAR(64),
    effective_from DATE NOT NULL,
    effective_to DATE,
    applicability_expression TEXT,
    required_facts JSONB DEFAULT '[]',
    required_evidence_types JSONB DEFAULT '[]',
    required_reviews JSONB DEFAULT '[]',
    required_approvals JSONB DEFAULT '[]',
    remediation_guide TEXT,
    rule_expression TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    author_id UUID REFERENCES users(id),
    approved_by_id UUID REFERENCES users(id),
    approved_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_requirement_version UNIQUE (requirement_id, version_number)
);

CREATE TABLE requirement_test_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requirement_version_id UUID NOT NULL REFERENCES requirement_versions(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    fixture_inputs JSONB NOT NULL DEFAULT '{}',
    expected_status VARCHAR(32) NOT NULL,
    last_run_at TIMESTAMP WITHOUT TIME ZONE,
    last_run_passed BOOLEAN,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

-- 3. Controls bound to Requirement Versions (D2)
CREATE TABLE regulatory_controls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requirement_version_id UUID NOT NULL REFERENCES requirement_versions(id) ON DELETE CASCADE,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    control_type VARCHAR(64) NOT NULL,
    expression TEXT,
    severity VARCHAR(32) NOT NULL DEFAULT 'HIGH',
    order_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

-- 4. Regulatory Snapshots for Rule-Set Pinning (D3)
CREATE TABLE regulatory_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    effective_date DATE NOT NULL,
    pinned_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    pinned_by_id UUID REFERENCES users(id),
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE regulatory_snapshot_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES regulatory_snapshots(id) ON DELETE CASCADE,
    requirement_version_id UUID NOT NULL REFERENCES requirement_versions(id),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_snapshot_item UNIQUE (snapshot_id, requirement_version_id)
);

-- 5. Regulatory Exceptions (D5)
CREATE TABLE regulatory_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    control_id UUID NOT NULL REFERENCES regulatory_controls(id),
    requirement_version_id UUID NOT NULL REFERENCES requirement_versions(id),
    reason TEXT NOT NULL,
    affected_state VARCHAR(64) NOT NULL,
    supporting_evidence_id UUID REFERENCES evidence(id),
    risk_classification VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    owner_id UUID NOT NULL REFERENCES users(id),
    approver_id UUID REFERENCES users(id),
    approved_at TIMESTAMP WITHOUT TIME ZONE,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    expiry_date DATE,
    mitigation_plan TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

-- 6. Control Evaluations (D2)
CREATE TABLE control_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    control_id UUID NOT NULL REFERENCES regulatory_controls(id),
    snapshot_id UUID REFERENCES regulatory_snapshots(id),
    status VARCHAR(32) NOT NULL,
    inputs_snapshot JSONB DEFAULT '{}',
    expression_evaluated TEXT,
    result_value VARCHAR(255),
    explanation TEXT NOT NULL,
    exception_id UUID REFERENCES regulatory_exceptions(id),
    evaluated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction_control UNIQUE (transaction_id, control_id)
);

-- 7. Transaction Lifecycle State Machine Transitions (D6)
CREATE TABLE transaction_lifecycle_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    from_state VARCHAR(64) NOT NULL,
    to_state VARCHAR(64) NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id),
    reason TEXT,
    blockers_snapshot JSONB DEFAULT '[]',
    exceptions_snapshot JSONB DEFAULT '[]',
    transitioned_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_req_ver_req ON requirement_versions(requirement_id);
CREATE INDEX idx_ctrl_req_ver ON regulatory_controls(requirement_version_id);
CREATE INDEX idx_snap_txn ON regulatory_snapshots(transaction_id);
CREATE INDEX idx_ctrl_eval_txn ON control_evaluations(transaction_id);
CREATE INDEX idx_reg_exc_txn ON regulatory_exceptions(transaction_id);
CREATE INDEX idx_txn_trans_txn ON transaction_lifecycle_transitions(transaction_id);

-- 8. Seed Statutory Requirements, Versions, Controls, and Test Cases
DO $$
DECLARE
    v_admin_id UUID;
    v_req_id UUID;
    v_ver_id UUID;
    v_ctrl_id UUID;
    v_txn RECORD;
    v_snap_id UUID;
BEGIN
    SELECT id INTO v_admin_id FROM users WHERE email = 'haroon@trinitycapital.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        SELECT id INTO v_admin_id FROM users LIMIT 1;
    END IF;

    -- Requirement 1: SEBI ICDR Regulation 238 (2025 Tiered Lock-In)
    INSERT INTO requirements (code, authority, jurisdiction, title, description, transaction_type, issue_type, severity, status)
    VALUES ('SEBI_ICDR_REG_238_2025', 'SEBI', 'INDIA', 'Minimum Promoters Contribution & 2025 Tiered Lock-In',
            'Minimum Promoters Contribution (20%) locked for 3 years; holding in excess of MPC locked 50% for 2 years and 50% for 1 year under 2025 ICDR amendment.',
            'SME_IPO', 'ALL', 'CRITICAL', 'ACTIVE')
    RETURNING id INTO v_req_id;

    INSERT INTO requirement_versions (requirement_id, version_number, citation, source_url, effective_from, status, author_id, approved_by_id, approved_at, remediation_guide, rule_expression)
    VALUES (v_req_id, 1, 'SEBI (ICDR) Regulations 2018, Regulation 238(1) & (2) as amended in 2025',
            'https://www.sebi.gov.in/legal/regulations/icdr-amendment-2025.html', '2025-01-01', 'ACTIVE', v_admin_id, v_admin_id, now(),
            'Ensure promoters subscribe to at least 20% post-issue capital and lock-in periods follow statutory 3yr/2yr/1yr tiers.',
            '{"field": "promoter_lock_in", "minMpcPct": 20.0, "excessSplit": [50.0, 50.0]}')
    RETURNING id INTO v_ver_id;

    INSERT INTO regulatory_controls (requirement_version_id, code, name, description, control_type, expression, severity, order_index)
    VALUES (v_ver_id, 'CTRL_SEBI_MPC_20PCT', 'Minimum Promoters Contribution 20% Lock-in',
            'Verifies that aggregate eligible promoter holding locked for 3 years constitutes at least 20% of post-issue capital.',
            'DOMAIN_RECONCILIATION', '{"check": "MPC_COVERAGE_AT_LEAST", "threshold": 20.0}', 'CRITICAL', 1);

    INSERT INTO requirement_test_cases (requirement_version_id, name, fixture_inputs, expected_status, last_run_passed)
    VALUES (v_ver_id, 'Valid 20% MPC with Tiered Excess', '{"mpcLockedPct": 20.5, "excessHoldings": 3500000}', 'PASS', true),
           (v_ver_id, 'Deficient MPC Shortfall', '{"mpcLockedPct": 17.8, "excessHoldings": 0}', 'FAIL', true);

    -- Requirement 2: SEBI ICDR Regulation 239 (Pre-Issue Non-Promoter Capital)
    INSERT INTO requirements (code, authority, jurisdiction, title, description, transaction_type, issue_type, severity, status)
    VALUES ('SEBI_ICDR_REG_239', 'SEBI', 'INDIA', 'Pre-Issue Non-Promoter Capital Lock-In & Exemptions',
            'Entire pre-issue capital held by persons other than promoters locked for 1 year, subject to statutory exemptions for ESOP shares and VCF/AIF Category I/II lookback holding.',
            'SME_IPO', 'ALL', 'HIGH', 'ACTIVE')
    RETURNING id INTO v_req_id;

    INSERT INTO requirement_versions (requirement_id, version_number, citation, source_url, effective_from, status, author_id, approved_by_id, approved_at, remediation_guide, rule_expression)
    VALUES (v_req_id, 1, 'SEBI (ICDR) Regulations 2018, Regulation 239',
            'https://www.sebi.gov.in/legal/regulations/icdr-2018.html', '2018-09-11', 'ACTIVE', v_admin_id, v_admin_id, now(),
            'Tag eligible non-promoter holdings with statutory exemption certificates or apply 1-year lock-in.',
            '{"field": "non_promoter_lock_in", "durationYears": 1}')
    RETURNING id INTO v_ver_id;

    INSERT INTO regulatory_controls (requirement_version_id, code, name, description, control_type, expression, severity, order_index)
    VALUES (v_ver_id, 'CTRL_SEBI_NON_PROMOTER_LOCKIN', 'Non-Promoter Capital 1-Year Lock-In or Exemption',
            'Verifies all pre-issue non-promoter equity is either locked for 1 year or mapped to an approved statutory exemption.',
            'DOMAIN_RECONCILIATION', '{"check": "NON_PROMOTER_LOCKIN_OR_EXEMPT"}', 'HIGH', 2);

    -- Requirement 3: BSE SME Post-Issue Paid-Up Capital Ceiling
    INSERT INTO requirements (code, authority, jurisdiction, title, description, transaction_type, issue_type, severity, status)
    VALUES ('BSE_SME_POST_ISSUE_CAPITAL', 'BSE', 'INDIA', 'BSE SME Post-Issue Capital Ceiling',
            'Post-issue face value capital shall not exceed ₹25 Crore to qualify for listing on the SME exchange platform.',
            'SME_IPO', 'ALL', 'CRITICAL', 'ACTIVE')
    RETURNING id INTO v_req_id;

    INSERT INTO requirement_versions (requirement_id, version_number, citation, source_url, effective_from, status, author_id, approved_by_id, approved_at, remediation_guide, rule_expression)
    VALUES (v_req_id, 1, 'BSE SME Listing Criteria §1.1 / SEBI ICDR Regulation 229(1)',
            'https://www.bseindia.com/sme/listing_criteria.html', '2024-01-01', 'ACTIVE', v_admin_id, v_admin_id, now(),
            'Restructure issue size or share capital so post-issue face value paid-up capital does not exceed ₹25 Cr.',
            '{"field": "issue.post_issue_paid_up_capital", "<=": 250000000}')
    RETURNING id INTO v_ver_id;

    INSERT INTO regulatory_controls (requirement_version_id, code, name, description, control_type, expression, severity, order_index)
    VALUES (v_ver_id, 'CTRL_BSE_SME_CAPITAL_CEILING', 'Post-Issue Paid-Up Capital <= 25 Cr',
            'Checks that post-issue face value paid-up capital does not exceed ₹25,00,00,000.',
            'DETERMINISTIC_EXPRESSION', '{"<=": [{"factValue": "issue.post_issue_paid_up_capital"}, 250000000]}', 'CRITICAL', 3);

    INSERT INTO requirement_test_cases (requirement_version_id, name, fixture_inputs, expected_status, last_run_passed)
    VALUES (v_ver_id, 'Eligible SME Capital 12 Cr', '{"issue.post_issue_paid_up_capital": 120000000}', 'PASS', true),
           (v_ver_id, 'Ineligible Capital 28 Cr Exceeds Limit', '{"issue.post_issue_paid_up_capital": 280000000}', 'FAIL', true);

    -- Requirement 4: BSE SME EBITDA Track Record
    INSERT INTO requirements (code, authority, jurisdiction, title, description, transaction_type, issue_type, severity, status)
    VALUES ('BSE_SME_EBITDA_TRACK_RECORD', 'BSE', 'INDIA', 'BSE SME 2-Year Positive EBITDA Track Record',
            'Operating profit (EBITDA) must be positive in at least 2 out of the 3 preceding financial years.',
            'SME_IPO', 'ALL', 'CRITICAL', 'ACTIVE')
    RETURNING id INTO v_req_id;

    INSERT INTO requirement_versions (requirement_id, version_number, citation, source_url, effective_from, status, author_id, approved_by_id, approved_at, remediation_guide, rule_expression)
    VALUES (v_req_id, 1, 'BSE SME Listing Eligibility Criteria §1.2',
            'https://www.bseindia.com/sme/listing_criteria.html', '2024-01-01', 'ACTIVE', v_admin_id, v_admin_id, now(),
            'Provide restated audited financial statements evidencing positive operating profit for 2 of the last 3 fiscal years.',
            '{">=": [{"countFactsAbove": ["issuer.ebitda", 0]}, 2]}')
    RETURNING id INTO v_ver_id;

    INSERT INTO regulatory_controls (requirement_version_id, code, name, description, control_type, expression, severity, order_index)
    VALUES (v_ver_id, 'CTRL_BSE_SME_EBITDA_TRACK', 'Operating Profit in at least 2 of 3 years',
            'Verifies positive operating EBITDA in at least two financial years.',
            'DETERMINISTIC_EXPRESSION', '{">=": [{"countFactsAbove": ["issuer.ebitda", 0]}, 2]}', 'CRITICAL', 4);

    -- Requirement 5: Companies Act 2013 §188 & SEBI LODR 23 RPT Governance
    INSERT INTO requirements (code, authority, jurisdiction, title, description, transaction_type, issue_type, severity, status)
    VALUES ('CA2013_LODR23_RPT_GOVERNANCE', 'MCA_SEBI', 'INDIA', 'Related Party Transactions Statutory Governance',
            'Audit committee approval and shareholder authorization for material related party transactions under CA 2013 §188 and SEBI LODR Reg 23.',
            'ALL', 'ALL', 'HIGH', 'ACTIVE')
    RETURNING id INTO v_req_id;

    INSERT INTO requirement_versions (requirement_id, version_number, citation, source_url, effective_from, status, author_id, approved_by_id, approved_at, remediation_guide, rule_expression)
    VALUES (v_req_id, 1, 'Companies Act 2013 §188 & SEBI (LODR) Regulations 2015, Regulation 23',
            'https://www.sebi.gov.in/legal/regulations/lodr-2015.html', '2022-04-01', 'ACTIVE', v_admin_id, v_admin_id, now(),
            'Obtain prior Audit Committee approval and passed shareholder resolution where transaction exceeds statutory materiality thresholds.',
            '{"check": "RPT_APPROVALS_SATISFIED"}')
    RETURNING id INTO v_ver_id;

    INSERT INTO regulatory_controls (requirement_version_id, code, name, description, control_type, expression, severity, order_index)
    VALUES (v_ver_id, 'CTRL_RPT_APPROVALS_VALIDATED', 'RPT Governance Approvals Reconciled',
            'Verifies all RPT transactions exceeding materiality thresholds have documented Audit Committee and Shareholder approvals.',
            'DOMAIN_RECONCILIATION', '{"check": "RPT_APPROVALS_SATISFIED"}', 'HIGH', 5);

    -- Requirement 6: SEBI ICDR Schedule VI Mandatory Litigation Clearance
    INSERT INTO requirements (code, authority, jurisdiction, title, description, transaction_type, issue_type, severity, status)
    VALUES ('SEBI_ICDR_SCHED_VI_LITIGATION', 'SEBI', 'INDIA', 'Schedule VI Mandatory Litigation Disclosures',
            'Mandatory disclosure and risk mitigation of all pending criminal proceedings, regulatory actions, and material civil litigation.',
            'ALL', 'ALL', 'CRITICAL', 'ACTIVE')
    RETURNING id INTO v_req_id;

    INSERT INTO requirement_versions (requirement_id, version_number, citation, source_url, effective_from, status, author_id, approved_by_id, approved_at, remediation_guide, rule_expression)
    VALUES (v_req_id, 1, 'SEBI (ICDR) Regulations 2018, Schedule VI Part A',
            'https://www.sebi.gov.in/legal/regulations/icdr-2018.html', '2018-09-11', 'ACTIVE', v_admin_id, v_admin_id, now(),
            'Resolve, obtain legal counsel opinion, or fully disclose all criminal proceedings involving the issuer, promoters, or directors.',
            '{"check": "NO_UNADDRESSED_CRIMINAL_LITIGATION"}')
    RETURNING id INTO v_ver_id;

    INSERT INTO regulatory_controls (requirement_version_id, code, name, description, control_type, expression, severity, order_index)
    VALUES (v_ver_id, 'CTRL_LITIGATION_CRIMINAL_CLEARED', 'Mandatory Criminal Litigation Review',
            'Verifies there are no unaddressed criminal proceedings against the issuer, promoters, or directors.',
            'DOMAIN_RECONCILIATION', '{"check": "NO_UNADDRESSED_CRIMINAL_LITIGATION"}', 'CRITICAL', 6);

    -- Requirement 7: Material Contracts Change of Control Consents
    INSERT INTO requirements (code, authority, jurisdiction, title, description, transaction_type, issue_type, severity, status)
    VALUES ('MATERIAL_CONTRACT_COC_CONSENTS', 'INTERNAL_POLICY', 'INDIA', 'Material Commercial Contracts CoC Consents',
            'Commercial agreements with Change of Control clauses must have executed waivers or written consents from counterparties prior to filing.',
            'ALL', 'ALL', 'HIGH', 'UNSOURCED')
    RETURNING id INTO v_req_id;

    INSERT INTO requirement_versions (requirement_id, version_number, citation, source_url, effective_from, status, author_id, approved_by_id, approved_at, remediation_guide, rule_expression)
    VALUES (v_req_id, 1, 'Merchant Banker Diligence Standard - Internal Policy Check',
            NULL, '2024-01-01', 'UNSOURCED', v_admin_id, v_admin_id, now(),
            'Procure and verify written Change of Control consent letters from lenders, joint venture partners, and key customers.',
            '{"check": "ALL_COC_CONSENTS_OBTAINED"}')
    RETURNING id INTO v_ver_id;

    INSERT INTO regulatory_controls (requirement_version_id, code, name, description, control_type, expression, severity, order_index)
    VALUES (v_ver_id, 'CTRL_CONTRACT_COC_CONSENTS', 'Change of Control Consent Verification',
            'Verifies written Change of Control consents are documented for all material contracts containing CoC covenants.',
            'DOMAIN_RECONCILIATION', '{"check": "ALL_COC_CONSENTS_OBTAINED"}', 'HIGH', 7);

    -- Create Initial Pinned Regulatory Snapshot for every existing Transaction
    FOR v_txn IN SELECT id, name FROM transactions LOOP
        INSERT INTO regulatory_snapshots (transaction_id, name, effective_date, pinned_at, pinned_by_id, notes, is_active)
        VALUES (v_txn.id, 'SEBI ICDR 2025.1 Standard Snapshot', '2026-01-01', now(), v_admin_id,
                'Initial statutory regulatory snapshot pinned upon Phase D activation', true)
        RETURNING id INTO v_snap_id;

        INSERT INTO regulatory_snapshot_items (snapshot_id, requirement_version_id)
        SELECT v_snap_id, rv.id FROM requirement_versions rv WHERE rv.status IN ('ACTIVE', 'UNSOURCED');
    END LOOP;

END $$;
