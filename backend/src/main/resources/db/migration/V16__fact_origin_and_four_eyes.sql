-- The candidate -> fact boundary and four-eyes verification (spec §4.2, §26, §73.1-3; plan B1).
--
-- origin records how a fact entered the transaction; materiality (seeded from the fact
-- definition) decides how much review it needs. A material fact may not be verified by the
-- person who recorded or accepted it.
ALTER TABLE facts
    ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN materiality VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

UPDATE facts f SET origin = 'CANDIDATE'
WHERE EXISTS (SELECT 1 FROM candidate_facts c WHERE c.resulting_fact_id = f.id);

UPDATE facts f SET materiality = d.default_materiality
FROM fact_definitions d WHERE d.fact_key = f.fact_key;

-- NOT VALID: enforced for every new or changed row; historical rows are left as recorded.
ALTER TABLE facts ADD CONSTRAINT chk_facts_four_eyes CHECK (
    NOT (status = 'VERIFIED' AND materiality IN ('MATERIAL', 'CRITICAL')
         AND verified_by_user_id = created_by_user_id)
) NOT VALID;
