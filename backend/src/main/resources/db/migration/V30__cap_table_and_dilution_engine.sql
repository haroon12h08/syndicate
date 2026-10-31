-- V30: Cap Table & Dilution Engine (Phase C, Ticket C3, Spec §20)
-- Models corporate actions, basic/diluted point-in-time shareholding, and SEBI ICDR 2025 amended lock-ins

CREATE TABLE security_classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    security_type VARCHAR(32) NOT NULL, -- 'EQUITY', 'PREFERENCE_CCPS', 'PREFERENCE_RPS', 'DEBENTURE_CCD', 'WARRANT', 'ESOP_POOL'
    isin VARCHAR(32),
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    face_value NUMERIC(15, 2) NOT NULL,
    voting_rights_per_share NUMERIC(10, 2) NOT NULL DEFAULT 1.00,
    authorized_shares NUMERIC(20, 0) NOT NULL,
    is_convertible BOOLEAN NOT NULL DEFAULT FALSE,
    conversion_ratio NUMERIC(15, 6) DEFAULT 1.000000,
    conversion_price NUMERIC(15, 2),
    conversion_deadline DATE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_security_classes_tx ON security_classes(transaction_id);

CREATE TABLE cap_table_holders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    party_id UUID,
    legal_name VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL, -- 'PROMOTER', 'PROMOTER_GROUP', 'PUBLIC_INSTITUTIONAL', 'PUBLIC_INDIVIDUAL', 'DIRECTOR_KMP', 'ESOP_TRUST', 'AIF_FVCI'
    pan VARCHAR(32),
    demat_account_no VARCHAR(64),
    residential_status VARCHAR(32) DEFAULT 'RESIDENT_INDIAN',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_cap_table_holders_tx ON cap_table_holders(transaction_id);

CREATE TABLE share_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    security_class_id UUID NOT NULL REFERENCES security_classes(id),
    event_type VARCHAR(32) NOT NULL, -- 'INCORPORATION', 'ALLOTMENT_PRIMARY', 'ALLOTMENT_PREFERENTIAL', 'BONUS_ISSUE', 'SHARE_SPLIT', 'TRANSFER', 'CONVERSION', 'ESOP_GRANT', 'ESOP_EXERCISE', 'BUYBACK'
    event_date DATE NOT NULL,
    source_holder_id UUID REFERENCES cap_table_holders(id),
    dest_holder_id UUID REFERENCES cap_table_holders(id),
    share_count NUMERIC(20, 0) NOT NULL,
    price_per_share NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    face_value_at_event NUMERIC(15, 2) NOT NULL,
    consideration_type VARCHAR(32) NOT NULL, -- 'CASH_BANK', 'BONUS_FREE_RESERVES', 'BONUS_REVALUATION', 'NON_CASH_TANGIBLE_ASSETS', 'NON_CASH_INTANGIBLES', 'CONVERSION_OF_DEBT'
    board_resolution_date DATE,
    shareholder_resolution_date DATE,
    roc_filing_ref VARCHAR(64),
    evidence_id UUID REFERENCES evidence(id),
    notes TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_share_count_positive CHECK (share_count > 0)
);

CREATE INDEX idx_share_transactions_tx_date ON share_transactions(transaction_id, event_date);

CREATE TABLE share_encumbrances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    holder_id UUID NOT NULL REFERENCES cap_table_holders(id),
    security_class_id UUID NOT NULL REFERENCES security_classes(id),
    encumbered_shares NUMERIC(20, 0) NOT NULL,
    encumbrance_type VARCHAR(32) NOT NULL, -- 'PLEDGE', 'NON_DISPOSAL_UNDERTAKING', 'NEGATIVE_LIEN', 'LOCK_IN'
    pledgee_name VARCHAR(255) NOT NULL,
    pledge_date DATE NOT NULL,
    release_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'RELEASED', 'INVOKED'
    evidence_id UUID REFERENCES evidence(id),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_encumbered_shares_positive CHECK (encumbered_shares > 0)
);

CREATE INDEX idx_share_encumbrances_tx ON share_encumbrances(transaction_id, holder_id);

CREATE TABLE share_lock_ins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    holder_id UUID NOT NULL REFERENCES cap_table_holders(id),
    security_class_id UUID NOT NULL REFERENCES security_classes(id),
    locked_share_count NUMERIC(20, 0) NOT NULL,
    lock_in_category VARCHAR(64) NOT NULL, -- 'PROMOTER_MINIMUM_CONTRIBUTION_3YR', 'PROMOTER_EXCESS_2YR', 'PROMOTER_EXCESS_1YR', 'PRE_ISSUE_NON_PROMOTER_1YR'
    regulatory_rule_id UUID,
    start_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    is_statutory BOOLEAN NOT NULL DEFAULT TRUE,
    exemption_claimed VARCHAR(64) DEFAULT 'NONE', -- 'ESOP_EXEMPTION', 'AIF_VCF_LOOKBACK_EXEMPTION', 'CONTINUITY_TRANSFER_EXEMPTION', 'NONE'
    evidence_id UUID REFERENCES evidence(id),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_locked_shares_positive CHECK (locked_share_count > 0)
);

CREATE INDEX idx_share_lock_ins_tx ON share_lock_ins(transaction_id, holder_id);

CREATE TABLE ipo_issue_parameters (
    transaction_id UUID PRIMARY KEY REFERENCES transactions(id) ON DELETE CASCADE,
    fresh_issue_shares NUMERIC(20, 0) NOT NULL DEFAULT 0,
    ofs_shares NUMERIC(20, 0) NOT NULL DEFAULT 0,
    price_band_lower NUMERIC(15, 2),
    price_band_upper NUMERIC(15, 2),
    expected_allotment_date DATE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);
