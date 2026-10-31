-- V31: Domain Registers for RPT, Litigation, Material Contracts & Statutory Rule Extensions
-- Phase C: Tickets C6 & C7, Spec §21, §22, §23, and D1 Foundation

-- 1. Extend existing Regulatory Rules with Rule Type & Parameter Specifications
ALTER TABLE regulatory_rules
    ADD COLUMN IF NOT EXISTS rule_type VARCHAR(32) NOT NULL DEFAULT 'FACT_CHECK',
    ADD COLUMN IF NOT EXISTS parameter_spec JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 2. Extend Rule Evaluations with Entity Type, Entity ID & Audit Inputs Snapshot
ALTER TABLE rule_evaluations
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS entity_id UUID,
    ADD COLUMN IF NOT EXISTS inputs_snapshot JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS formula_expression TEXT;

CREATE INDEX IF NOT EXISTS idx_rule_evaluations_entity ON rule_evaluations(transaction_id, entity_type, entity_id);

-- 3. Related Parties Directory
CREATE TABLE related_parties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    party_type VARCHAR(32) NOT NULL, -- 'INDIVIDUAL', 'BODY_CORPORATE', 'FIRM', 'HUF', 'TRUST'
    pan VARCHAR(32),
    din_cin VARCHAR(32),
    relationship_nature VARCHAR(64) NOT NULL, -- 'PROMOTER', 'DIRECTOR', 'KMP', 'RELATIVE_OF_DIRECTOR_OR_KMP', 'ENTITY_WITH_SIGNIFICANT_INFLUENCE', 'SUBSIDIARY', 'ASSOCIATE_OR_JV'
    relative_details VARCHAR(255),
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_suggested BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_by_user_id UUID REFERENCES users(id),
    confirmed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_related_parties_tx ON related_parties(transaction_id);

-- 4. Related Party Transactions Ledger
CREATE TABLE related_party_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    related_party_id UUID NOT NULL REFERENCES related_parties(id),
    financial_year VARCHAR(32) NOT NULL,
    transaction_type VARCHAR(64) NOT NULL, -- 'SALE_OF_GOODS', 'PURCHASE_OF_GOODS', 'RENDERING_OF_SERVICES', 'RECEIVING_OF_SERVICES', 'LEASING_OF_PROPERTY', 'LOAN_ADVANCE_GIVEN', 'LOAN_ADVANCE_TAKEN', 'GUARANTEE_OR_COLLATERAL', 'REMUNERATION_AND_PERQUISITES', 'BRAND_USAGE_OR_ROYALTY'
    amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    in_ordinary_course BOOLEAN NOT NULL DEFAULT TRUE,
    is_arms_length BOOLEAN NOT NULL DEFAULT TRUE,
    arm_length_basis VARCHAR(512),
    audit_committee_approval VARCHAR(32) NOT NULL, -- 'OMNIBUS', 'SPECIFIC', 'EXEMPT', 'NONE'
    audit_committee_approval_date DATE,
    audit_committee_max_amount NUMERIC(18, 2),
    board_approval_date DATE,
    shareholder_approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    shareholder_approval_date DATE,
    ca2013_sec188_applicable BOOLEAN NOT NULL DEFAULT FALSE,
    sebi_lodr_material BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_rpt_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_rpt_tx_party ON related_party_transactions(transaction_id, related_party_id);

CREATE TABLE rpt_evidence_link (
    rpt_id UUID NOT NULL REFERENCES related_party_transactions(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    evidence_role VARCHAR(64) NOT NULL, -- 'AUDITED_NOTE', 'BOARD_RESOLUTION', 'AUDIT_COMMITTEE_APPROVAL', 'SHAREHOLDER_RESOLUTION', 'TRANSFER_PRICING_STUDY'
    PRIMARY KEY (rpt_id, evidence_id, evidence_role)
);

-- 5. Litigation Cases Register
CREATE TABLE litigation_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    workstream_id UUID NOT NULL REFERENCES workstreams(id),
    case_number VARCHAR(128) NOT NULL,
    court_forum VARCHAR(255) NOT NULL,
    jurisdiction_city VARCHAR(128),
    case_title VARCHAR(512) NOT NULL,
    category VARCHAR(64) NOT NULL, -- 'CRIMINAL_PROCEEDING', 'REGULATORY_STATUTORY_ACTION', 'EXCHANGE_DISCIPLINARY_ACTION', 'DIRECT_TAX', 'INDIRECT_TAX', 'MATERIAL_CIVIL_LITIGATION', 'INSOLVENCY_BANKRUPTCY', 'LABOUR_INDUSTRIAL_DISPUTES'
    entity_type VARCHAR(32) NOT NULL, -- 'ISSUER', 'PROMOTER', 'DIRECTOR', 'SUBSIDIARY', 'GROUP_COMPANY'
    entity_name VARCHAR(255) NOT NULL,
    party_role VARCHAR(32) NOT NULL, -- 'PETITIONER_PLAINTIFF', 'RESPONDENT_DEFENDANT', 'APPELLANT'
    opposite_party VARCHAR(255) NOT NULL,
    filing_date DATE,
    claimed_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    contingent_liability_exposure NUMERIC(18, 2) NOT NULL DEFAULT 0,
    provision_made NUMERIC(18, 2) DEFAULT 0,
    potential_impact VARCHAR(32) NOT NULL, -- 'FINANCIAL', 'OPERATIONAL', 'REPUTATIONAL', 'MANAGEMENT_DISQUALIFICATION', 'PERMIT_REVOCATION'
    case_summary TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'STAYED', 'APPEAL_PREFERRED', 'DISPOSED_FAVORABLE', 'DISPOSED_UNFAVORABLE', 'SETTLED'
    is_material BOOLEAN NOT NULL DEFAULT FALSE,
    materiality_basis VARCHAR(128), -- 'CRIMINAL_MANDATORY', 'REGULATORY_MANDATORY', 'NET_WORTH_THRESHOLD', 'TURNOVER_THRESHOLD', 'QUALITATIVE_OPERATION'
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_litigation_cases_tx ON litigation_cases(transaction_id, category);

CREATE TABLE litigation_evidence_link (
    litigation_id UUID NOT NULL REFERENCES litigation_cases(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    evidence_role VARCHAR(64) NOT NULL, -- 'PETITION', 'COURT_ORDER', 'LEGAL_OPINION', 'TAX_DEMAND_NOTICE', 'SETTLEMENT_ORDER'
    PRIMARY KEY (litigation_id, evidence_id, evidence_role)
);

-- 6. Material Commercial Contracts Register
CREATE TABLE material_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    workstream_id UUID NOT NULL REFERENCES workstreams(id),
    contract_reference VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    contract_category VARCHAR(64) NOT NULL, -- 'KEY_CUSTOMER_CONTRACT', 'KEY_VENDOR_CONTRACT', 'EXCLUSIVE_SUPPLY_OR_DISTRIBUTION', 'INTELLECTUAL_PROPERTY_LICENSE', 'FINANCING_OR_SECURITY_AGREEMENT', 'SHAREHOLDERS_AGREEMENT', 'OTHER_MATERIAL_COMMERCIAL'
    counterparty_name VARCHAR(255) NOT NULL,
    is_related_party BOOLEAN NOT NULL DEFAULT FALSE,
    related_party_id UUID REFERENCES related_parties(id),
    execution_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    is_perpetual BOOLEAN NOT NULL DEFAULT FALSE,
    contract_value_annual NUMERIC(18, 2),
    contract_value_total NUMERIC(18, 2),
    revenue_percentage NUMERIC(6, 2),
    supply_percentage NUMERIC(6, 2),
    is_above_10_pct_screening_indicator BOOLEAN NOT NULL DEFAULT FALSE,
    has_change_of_control BOOLEAN NOT NULL DEFAULT FALSE,
    change_of_control_summary TEXT,
    change_of_control_consent_obtained BOOLEAN NOT NULL DEFAULT FALSE,
    has_exclusivity BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'EXPIRING_SOON', 'EXPIRED', 'TERMINATED', 'UNDER_AMENDMENT'
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'REVIEWED', 'REQUIRES_AMENDMENT', 'CONSENT_PENDING'
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_material_contracts_tx ON material_contracts(transaction_id, contract_category);

CREATE TABLE material_contract_evidence_link (
    contract_id UUID NOT NULL REFERENCES material_contracts(id) ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    evidence_role VARCHAR(64) NOT NULL, -- 'EXECUTED_CONTRACT', 'AMENDMENT', 'CHANGE_OF_CONTROL_CONSENT', 'TERMINATION_NOTICE'
    PRIMARY KEY (contract_id, evidence_id, evidence_role)
);

-- 7. Seed Statutory Rules (2025 ICDR Amendment & MCA/LODR RPTs)
INSERT INTO regulatory_rules (
    code, title, description, workstream_type, severity, fact_key,
    source_authority, source_citation, source_url, effective_from, version,
    provenance_status, minimum_facts, expression, requirement_summary, active,
    rule_type, parameter_spec
) VALUES
(
    'SEBI_ICDR_REG_238_2025',
    'Promoter Lock-In Tiered Schedule (2025 Amendment)',
    '20% Minimum Promoters Contribution locked in for 3 years; excess holding tiered: 50% for 2 years, remaining 50% for 1 year.',
    'CAPITAL_STRUCTURE',
    'BLOCKING',
    'issuer.promoter_ownership_pct',
    'SEBI',
    'SEBI ICDR Regulations 2018, Regulation 238(a) & 238(b)',
    'https://www.sebi.gov.in/legal/regulations/sebi-icdr-regulations-2018.html',
    '2025-01-01',
    1,
    'VERIFIED_STATUTE',
    1,
    '{"mpc_percentage": 20.0, "mpc_years": 3, "excess_tier1_pct": 50.0, "excess_tier1_years": 2, "excess_tier2_pct": 50.0, "excess_tier2_years": 1}',
    'Promoter lock-in must comply with SEBI ICDR 2025 amendment tiered schedule.',
    TRUE,
    'CAP_TABLE_LOCK_IN',
    '{"mpc_percentage": 20.0, "mpc_lock_in_years": 3, "excess_tier1_percentage": 50.0, "excess_tier1_years": 2, "excess_tier2_percentage": 50.0, "excess_tier2_years": 1}'::jsonb
),
(
    'SEBI_ICDR_REG_239',
    'Lock-in of Pre-Issue Non-Promoter Capital',
    'Pre-issue capital of non-promoters locked in for 1 year, subject to statutory ESOP and AIF/VCF holding period exemptions.',
    'CAPITAL_STRUCTURE',
    'BLOCKING',
    'issuer.share_capital',
    'SEBI',
    'SEBI ICDR Regulations 2018, Regulation 239',
    'https://www.sebi.gov.in/legal/regulations/sebi-icdr-regulations-2018.html',
    '2018-09-11',
    1,
    'VERIFIED_STATUTE',
    1,
    '{"lock_in_years": 1, "exemptions": ["ESOP_EXEMPTION", "AIF_VCF_LOOKBACK_EXEMPTION", "CONTINUITY_TRANSFER_EXEMPTION"]}',
    'Entire pre-issue non-promoter equity locked for 1 year from allotment date.',
    TRUE,
    'CAP_TABLE_LOCK_IN',
    '{"lock_in_years": 1, "exemptions": ["ESOP_EXEMPTION", "AIF_VCF_LOOKBACK_EXEMPTION", "CONTINUITY_TRANSFER_EXEMPTION"]}'::jsonb
),
(
    'CA2013_SEC_188_GOODS',
    'Sale, Purchase or Supply of Goods & Materials RPT Threshold',
    'Shareholder approval required if transaction exceeds 10% of company turnover, unless entered in ordinary course on arm length basis.',
    'CORPORATE_SECRETARIAL',
    'HIGH',
    'issuer.revenue',
    'MCA',
    'Companies Act 2013, Section 188(1)(a) read with Rule 15',
    'https://www.mca.gov.in/content/mca/global/en/acts-rules/ebooks/acts.html',
    '2014-04-01',
    1,
    'VERIFIED_STATUTE',
    1,
    '{"threshold_basis": "TURNOVER", "threshold_pct": 10.0, "exemption": "ORDINARY_COURSE_AND_ARMS_LENGTH"}',
    'Goods RPTs >10% turnover require prior ordinary resolution unless exempt.',
    TRUE,
    'RPT_MATERIALITY',
    '{"threshold_basis": "TURNOVER", "threshold_percentage": 10.0, "exemption_basis": "ORDINARY_COURSE_AND_ARMS_LENGTH"}'::jsonb
),
(
    'SEBI_LODR_REG_23_MATERIAL',
    'Material Related Party Transactions Threshold (Post-2022 Amendment)',
    'RPT is material if it exceeds lower of ₹1,000 Crore or 10% of annual consolidated turnover; brand/royalty threshold is 5%.',
    'LEGAL_DUE_DILIGENCE',
    'BLOCKING',
    'issuer.revenue',
    'SEBI',
    'SEBI LODR Regulations 2015, Regulation 23(4)',
    'https://www.sebi.gov.in/legal/regulations/sebi-lodr-regulations-2015.html',
    '2022-04-01',
    2,
    'VERIFIED_STATUTE',
    1,
    '{"threshold_absolute_inr": 10000000000.0, "threshold_turnover_pct": 10.0, "royalty_brand_pct": 5.0}',
    'Transactions exceeding lower of ₹1000 Cr or 10% turnover require shareholder approval.',
    TRUE,
    'RPT_MATERIALITY',
    '{"threshold_absolute_inr": 10000000000.0, "threshold_turnover_percentage": 10.0, "royalty_brand_percentage": 5.0, "rule_logic": "LOWER_OF_ABSOLUTE_OR_TURNOVER"}'::jsonb
);
