-- Disclosure placeholders reference fact keys, not display labels (spec §15; plan E1).
--
-- {{fact:Revenue}} silently broke whenever a label was edited, and two facts could answer to the
-- same words. {{fact:issuer.revenue}} names the thing itself. An optional @period picks the
-- period: {{fact:issuer.revenue@FY2026}}.
UPDATE disclosures d
SET body_template = regexp_replace(d.body_template,
        '\{\{fact:' || a.alias || '\}\}',
        '{{fact:' || a.fact_key || '}}',
        'gi')
FROM fact_definition_aliases a
WHERE lower(d.body_template) LIKE '%{{fact:' || a.alias || '}}%';

-- Anything still written as a free-text label is rewritten to the custom key that label produced,
-- which is the same rule the application applies when a fact is recorded under that label.
UPDATE disclosures d
SET body_template = regexp_replace(d.body_template,
        '\{\{fact:' || f.label || '\}\}',
        '{{fact:' || f.fact_key || '}}',
        'gi')
FROM facts f
JOIN workstreams w ON w.id = f.workstream_id
WHERE w.transaction_id = d.transaction_id
  AND lower(d.body_template) LIKE '%{{fact:' || lower(f.label) || '}}%';
