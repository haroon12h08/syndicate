-- Fact semantic identity (spec §4.3, §11, §15; plan A3).
--
-- fact_key is the stable identifier of *what* a fact describes; label becomes display text.
-- lineage_id is constant across every version of the same fact, so references that must follow
-- corrections point at the lineage while reviews/approvals point at a concrete version id.

CREATE TABLE fact_definitions (
    fact_key VARCHAR(128) PRIMARY KEY,
    display_label VARCHAR(255) NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    is_financial BOOLEAN NOT NULL,
    default_materiality VARCHAR(16) NOT NULL
);

CREATE TABLE fact_definition_aliases (
    alias VARCHAR(255) PRIMARY KEY,
    fact_key VARCHAR(128) NOT NULL REFERENCES fact_definitions(fact_key)
);

INSERT INTO fact_definitions (fact_key, display_label, value_type, is_financial, default_materiality) VALUES
    ('issuer.revenue', 'Revenue from operations', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.other_income', 'Other income', 'MONEY', TRUE, 'NORMAL'),
    ('issuer.total_income', 'Total income', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.ebitda', 'EBITDA', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.ebit', 'EBIT', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.pat', 'Profit after tax', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.total_assets', 'Total assets', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.total_liabilities', 'Total liabilities', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.net_worth', 'Net worth', 'MONEY', TRUE, 'CRITICAL'),
    ('issuer.borrowings', 'Total borrowings', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.working_capital', 'Working capital', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.inventory', 'Inventory', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.trade_receivables', 'Trade receivables', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.trade_payables', 'Trade payables', 'MONEY', TRUE, 'NORMAL'),
    ('issuer.cash', 'Cash and cash equivalents', 'MONEY', TRUE, 'NORMAL'),
    ('issuer.capex', 'Capital expenditure', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.share_capital', 'Paid-up share capital', 'MONEY', TRUE, 'CRITICAL'),
    ('issuer.authorized_capital', 'Authorised share capital', 'MONEY', FALSE, 'MATERIAL'),
    ('issuer.face_value', 'Face value per share', 'MONEY', FALSE, 'MATERIAL'),
    ('issuer.share_count', 'Equity shares outstanding', 'INTEGER', FALSE, 'CRITICAL'),
    ('issuer.promoter_ownership_pct', 'Promoter shareholding', 'PERCENT', FALSE, 'CRITICAL'),
    ('issue.size', 'Issue size', 'MONEY', FALSE, 'CRITICAL'),
    ('issue.price', 'Issue price per share', 'MONEY', FALSE, 'CRITICAL'),
    ('issue.fresh_issue_shares', 'Fresh issue shares', 'INTEGER', FALSE, 'CRITICAL'),
    ('issue.ofs_shares', 'Offer for sale shares', 'INTEGER', FALSE, 'CRITICAL'),
    ('issuer.rpt_total', 'Related-party transactions total', 'MONEY', TRUE, 'MATERIAL'),
    ('issuer.litigation_exposure', 'Aggregate litigation exposure', 'MONEY', FALSE, 'MATERIAL'),
    ('issuer.licence_expiry', 'Material licence expiry', 'DATE', FALSE, 'MATERIAL'),
    ('issuer.incorporation_date', 'Date of incorporation', 'DATE', FALSE, 'NORMAL'),
    ('issuer.employee_count', 'Employees', 'INTEGER', FALSE, 'LOW');

INSERT INTO fact_definition_aliases (alias, fact_key) VALUES
    ('revenue', 'issuer.revenue'),
    ('total revenue', 'issuer.revenue'),
    ('revenue from operations', 'issuer.revenue'),
    ('turnover', 'issuer.revenue'),
    ('net sales', 'issuer.revenue'),
    ('other income', 'issuer.other_income'),
    ('total income', 'issuer.total_income'),
    ('ebitda', 'issuer.ebitda'),
    ('ebit', 'issuer.ebit'),
    ('pat', 'issuer.pat'),
    ('net profit', 'issuer.pat'),
    ('profit after tax', 'issuer.pat'),
    ('total assets', 'issuer.total_assets'),
    ('total liabilities', 'issuer.total_liabilities'),
    ('net worth', 'issuer.net_worth'),
    ('borrowings', 'issuer.borrowings'),
    ('total borrowings', 'issuer.borrowings'),
    ('total debt', 'issuer.borrowings'),
    ('working capital', 'issuer.working_capital'),
    ('inventory', 'issuer.inventory'),
    ('inventories', 'issuer.inventory'),
    ('receivables', 'issuer.trade_receivables'),
    ('trade receivables', 'issuer.trade_receivables'),
    ('debtors', 'issuer.trade_receivables'),
    ('payables', 'issuer.trade_payables'),
    ('trade payables', 'issuer.trade_payables'),
    ('creditors', 'issuer.trade_payables'),
    ('cash', 'issuer.cash'),
    ('cash and cash equivalents', 'issuer.cash'),
    ('capex', 'issuer.capex'),
    ('capital expenditure', 'issuer.capex'),
    ('share capital', 'issuer.share_capital'),
    ('paid-up capital', 'issuer.share_capital'),
    ('paid up capital', 'issuer.share_capital'),
    ('paid-up share capital', 'issuer.share_capital'),
    ('authorized capital', 'issuer.authorized_capital'),
    ('authorised capital', 'issuer.authorized_capital'),
    ('authorised share capital', 'issuer.authorized_capital'),
    ('face value', 'issuer.face_value'),
    ('shares outstanding', 'issuer.share_count'),
    ('number of shares', 'issuer.share_count'),
    ('equity shares outstanding', 'issuer.share_count'),
    ('promoter ownership', 'issuer.promoter_ownership_pct'),
    ('promoter shareholding', 'issuer.promoter_ownership_pct'),
    ('promoter holding', 'issuer.promoter_ownership_pct'),
    ('issue size', 'issue.size'),
    ('issue price', 'issue.price'),
    ('fresh issue', 'issue.fresh_issue_shares'),
    ('fresh issue shares', 'issue.fresh_issue_shares'),
    ('offer for sale', 'issue.ofs_shares'),
    ('ofs', 'issue.ofs_shares'),
    ('related party transactions', 'issuer.rpt_total'),
    ('related-party transactions', 'issuer.rpt_total'),
    ('litigation amount', 'issuer.litigation_exposure'),
    ('litigation exposure', 'issuer.litigation_exposure'),
    ('licence expiry', 'issuer.licence_expiry'),
    ('license expiry', 'issuer.licence_expiry'),
    ('incorporation date', 'issuer.incorporation_date'),
    ('date of incorporation', 'issuer.incorporation_date'),
    ('employees', 'issuer.employee_count'),
    ('employee count', 'issuer.employee_count'),
    ('headcount', 'issuer.employee_count');


ALTER TABLE facts
    ADD COLUMN fact_key VARCHAR(128),
    ADD COLUMN lineage_id UUID;

-- Existing facts: known labels map through the alias table; anything else gets a custom key
-- derived from its label, the same rule the application applies to new free-text labels.
UPDATE facts f
SET fact_key = COALESCE(
        (SELECT a.fact_key FROM fact_definition_aliases a
         WHERE a.alias = lower(regexp_replace(trim(f.label), '\s+', ' ', 'g'))),
        'custom.' || trim(both '_' from regexp_replace(lower(trim(f.label)), '[^a-z0-9]+', '_', 'g')));

-- Lineage is the id of the first version in each supersede chain.
WITH RECURSIVE chain AS (
    SELECT id, id AS root FROM facts WHERE supersedes_fact_id IS NULL
    UNION ALL
    SELECT f.id, c.root FROM facts f JOIN chain c ON f.supersedes_fact_id = c.id
)
UPDATE facts f SET lineage_id = chain.root FROM chain WHERE chain.id = f.id;

ALTER TABLE facts
    ALTER COLUMN fact_key SET NOT NULL,
    ALTER COLUMN lineage_id SET NOT NULL;

CREATE INDEX idx_facts_fact_key ON facts (fact_key);
CREATE INDEX idx_facts_lineage ON facts (lineage_id, version);
