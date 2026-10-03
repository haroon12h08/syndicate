# Syndicate v2 — Build Plan

**Source spec:** [`syndicate_v2.md`](../../syndicate_v2.md) ("Product & Engineering Bible"). Section references below are written `§N`.
**Audience:** SDEs and coding agents implementing Syndicate.
**Date:** 2026-09-21
**Starting point:** commit `2058cdf` (Spring Boot 3.3 / Java 17 modular monolith, React 19 + Vite, PostgreSQL + Flyway V1–V9, RabbitMQ, Docker Compose).

---

## 0. How to use this plan

1. Read §1 (product), §4 (object model), §41 (principles), §73 (invariants) of the spec once. Everything below exists to serve those.
2. Pick the lowest-numbered **unblocked** ticket in the current phase (see the dependency column). Phases are sequential; tickets inside a phase can often run in parallel.
3. Each ticket lists: **Goal**, **Spec refs**, **Changes** (backend / DB / frontend), **Acceptance criteria**. A ticket is done only when every acceptance criterion is proven by an automated test (see §3 Definition of Done).
4. Do not build beyond the ticket. The spec is explicit (§43, §70): rigor over breadth. If a ticket seems to need a new entity not listed, stop and raise it.
5. When a ticket changes a cross-cutting contract (IDs, events, dependency graph, permission model), update §2 of this plan in the same PR.

### Agent operating rules
- One ticket per branch / PR. Branch name: `v2/<ticket-id>-<slug>` (e.g. `v2/A3-fact-semantic-keys`).
- Never edit an applied Flyway migration. Always add `V<next>__<name>.sql`. Next free number at plan time: **V10**. Check `backend/src/main/resources/db/migration/` before choosing; two parallel PRs must not claim the same number — rebase and renumber if they collide.
- Write the failing test first (invariant tests especially), then the code.
- Backend package per domain under `com.syndicate.<domain>` with the existing layout: `Entity`, `Repository`, `Service`, `Controller`, `dto/`. Match it.
- Frontend: API wrapper in `frontend/src/api/<domain>.js`, components in `components/`, pages in `pages/`. Plain JSX, no new state library unless a ticket says so.
- No AI/LLM calls anywhere until Phase G. Candidate extraction stays deterministic (PDFBox + Tesseract) until then.

---

## 1. Where we are vs. where the spec wants us

What exists (§40) and the gap that matters:

| Area | Today (code) | Gap vs spec | Fixed in |
|---|---|---|---|
| Tests | 9 unit tests (ingestion, checksum, candidate) | No integration, security, E2E, or invariant tests (§55) | A1 |
| Authorization | `PermissionService` with 7 permissions; JWT 24h | No endpoint×role matrix test; no object-level policy layer; long-lived token (§29–30, §51–52) | A2, H2 |
| Evidence storage | Local filesystem `FileStorageService`, SHA-256 | No versioning, quality state, custody, object storage, retention (§7, §50, §53) | A4, A5, H3 |
| Audit | `AuditService.record` swallows exceptions | Must be same-transaction, append-only, chained (§33) | A6 |
| Facts | Single `facts` table, `label`+`value` strings, bitemporal columns, `supersedes_fact_id` | No semantic key, no typed value, no subject entity, no materiality/sensitivity (§4.3, §10) | A3, B5 |
| Conflicts | Keyed on lowercased `label + period` | Must key on semantic identity (subject/predicate/period/context) and distinguish temporal succession (§11) | A3, B6 |
| Disclosures | `{{fact:Label}}` regex placeholders, stale propagation in `ChangePropagationService` | Placeholders must reference stable IDs; dependencies must be explicit rows (§15) | A7, E1 |
| Dependencies | Implicit (label lookup) | Generic relational dependency graph + impact traversal (§5, §6) | A7, B7 |
| Claims, Requirements, Controls, Reviews, Approvals, Diligence Questions, Exceptions | Absent (rules exist as `RegulatoryRule` + `RuleEvaluation`) | First-class objects (§4.4–4.8, §9, §39) | B, D |
| Transaction lifecycle | `DRAFT/ACTIVE/CLOSED` | Configurable state machine with preconditions (§37–38) | D6 |
| Domain entities | Company only | Cap table, related parties, litigation, contracts, people (§20–24) | C |
| Readiness | Rule-based `ReadinessService` | Blocker-derived, explainable, 5 states (§14) | B9 |
| Provenance | Merkle manifest per DRHP | Must also cover rule versions, approvals, app version; independently verifiable (§34) | E5 |
| Registry | `MockMcaRegistryAdapter` | Treat as evidence; raw response + hash; conflicts not auto-resolved (§25) | C8 |

Assume the existing code is correct unless a ticket changes it. Do **not** rewrite the architecture (§40, §43): no microservices, no graph DB.

---

## 2. Cross-cutting contracts (decide once, use everywhere)

These are built in Phase A and every later ticket depends on them. Changes require updating this section.

### 2.1 Identifiers
- All primary keys are UUIDs (existing `BaseEntity`).
- **Semantic key** for facts (`fact_key`): a stable string `subject:predicate[:qualifier]`, e.g. `company:{companyId}:revenue`, `shareholder:{partyId}:ownership_pct`. The period is a separate column, not part of the key. Keys come from a **fact-definition catalog** table (`fact_definitions`: key pattern, value type, unit class, default materiality, workstream type, is_financial). Free-text labels become display metadata only.
- **Lineage ID**: every versioned object has `lineage_id` (constant across versions) + `version` (int, starts at 1) + `id` (per-version). References that must survive new versions (e.g. "this disclosure uses promoter ownership") point at `lineage_id`; references that freeze state (reviews, approvals, compilations, provenance) point at the per-version `id`.

### 2.2 Versioned-object pattern
Applies to Fact, Evidence, Claim, Requirement, Disclosure, Document. Rules:
- Material changes insert a new row; the previous row gets `system_superseded_at = now()` and status `SUPERSEDED`. No `UPDATE` of value columns on a row once it's `VERIFIED`/`APPROVED`.
- Optimistic concurrency: every mutation request carries `expectedVersion`; mismatch → HTTP 409 (§46). Use JPA `@Version` on a separate `row_version` column for concurrency, distinct from the business `version`.
- Bitemporal: `valid_from`/`valid_to` (business) + `created_at`/`system_superseded_at` (system), as already done for facts in V4. Provide one shared query helper `asOf(systemTime, validTime)`.

### 2.3 Dependency graph (`dependency_edges`)
One generic table, built in A7:

```
dependency_edges(
  id, transaction_id,
  from_type, from_lineage_id,      -- the dependent (e.g. DISCLOSURE)
  to_type,   to_lineage_id,        -- the dependency (e.g. FACT)
  kind,                            -- USES_VALUE | SUPPORTED_BY | EVALUATES | REVIEWS | APPROVES | ANSWERS | DERIVED_FROM
  pinned_version_id NULL,          -- version the dependent last validated against
  created_at, created_by
)
```
- Edge direction: `from` depends on `to`. Impact traversal walks edges in reverse (from `to` → all `from`s), breadth-first, cycle-safe, transaction-scoped.
- Staleness rule: a dependent is **stale** if any edge's `pinned_version_id` ≠ current version id of `to_lineage_id`. This single rule drives disclosure staleness, calculation staleness, review/approval invalidation.
- Object types enum (`GraphNodeType`): `EVIDENCE, FACT, CLAIM, REQUIREMENT, CONTROL, DISCLOSURE, DOCUMENT, REVIEW, APPROVAL, DILIGENCE_QUESTION, OBSERVATION, CALCULATION, EXCEPTION`.

### 2.4 Domain events (§47)
- Java records under `com.syndicate.events`, carrying `transactionId`, object type, `lineageId`, `versionId`, `actorId`, `correlationId`, `occurredAt`. No payload blobs (§47).
- Persist to an **outbox** table (`domain_events`) in the same DB transaction as the state change; a relay publishes to RabbitMQ. Synchronous consequences that must be true before the API returns (fact verified, conflict raised) happen in-transaction; derived work (readiness recompute, notifications, tasks, indexing) is async from the outbox (§49).
- Consumers are idempotent: `processed_events(consumer, event_id)` unique constraint (§48).

### 2.5 Idempotency keys (§48)
Format `"{transactionId}:{sourceType}:{sourceVersionId}:{operation}"`. Unique index on generated tasks, notifications, candidate facts, compilations. Mutating HTTP endpoints that can be retried (upload, accept candidate, approve, compile) accept an `Idempotency-Key` header, stored in `idempotency_records` for 24h.

### 2.6 Mutation pipeline (§46)
Every mutating service method follows the same order; put it in a small helper rather than repeating it:
1. `authz.require(actor, permission, target)` → 403
2. load + check `expectedVersion` → 409
3. validate business invariants → 422 with a machine-readable `code`
4. write state
5. `audit.append(...)` — same DB transaction; failure rolls back (§33, replaces current swallow)
6. `events.publish(...)` to outbox
7. return the new `{id, lineageId, version, status}`

### 2.7 Authorization model (§29–30, §52)
`User + Organization + Transaction + Role + Workstream + Permission`. Implement a `PolicyService` with one method per protected action (e.g. `canVerifyFact(user, fact)`), backed by a **declarative role→permission matrix** (`permission_grants` seed data, versioned in a migration). Every controller method must call it; an ArchUnit test fails the build if a `@RestController` mutating method doesn't.

### 2.8 Error contract
Extend existing `ApiError` with `code` (stable string, e.g. `FACT_VERSION_CONFLICT`, `CANDIDATE_CANNOT_VERIFY`, `COMPILATION_BLOCKED`) and `details` (list of blocker objects). The frontend switches on `code`, never on message text.

---

## 3. Definition of Done (every ticket)

- [ ] Acceptance criteria each proven by an automated test.
- [ ] Backend: `./mvnw verify` green (unit + Testcontainers integration).
- [ ] Frontend: `npm run lint` and `npm run build` green; Vitest tests for new logic.
- [ ] New endpoints: added to the authorization matrix test (A2) with expected allow/deny per role.
- [ ] New mutations: follow §2.6; emit audit + domain event.
- [ ] New versioned objects: follow §2.2.
- [ ] Migration is additive and runs on a DB at the previous version with seed data (migration test in A1).
- [ ] No invariant test (§5 below) regresses.
- [ ] UI states the difference between *candidate / accepted / verified / stale / superseded* visually wherever the object appears.
- [ ] README or this plan updated if a contract changed.

---

## 4. Phases and tickets

Phases map to spec §69. Order within the spec's §70 "what to build next" list is respected. Phase C (domain depth) and Phase D (regulatory engine) may run in parallel once Phase B is done.

```
A Integrity foundation ──► B Diligence control ──┬─► C Domain depth ──┐
                                                 └─► D Regulatory eng ─┴─► E Disclosure control ─► F Filing & observations ─► G Intelligence
H Production hardening runs alongside, must finish before first customer pilot.
```

### Phase A — Integrity foundation (§69 A)

| ID | Ticket | Depends on |
|---|---|---|
| A1 | Test harness | — |
| A2 | Authorization matrix + policy layer | A1 |
| A3 | Fact semantic keys + typed values | A1 |
| A4 | Evidence versioning + custody | A1 |
| A5 | Evidence quality state | A4 |
| A6 | Transactional, append-only, hash-chained audit | A1 |
| A7 | Dependency graph + domain-event outbox | A3, A6 |
| A8 | Optimistic concurrency on mutations | A3 |

**A1 — Test harness.** *Goal:* make the stateful system testable (§55).
- Add Testcontainers (PostgreSQL, RabbitMQ) + `@SpringBootTest` base class `IntegrationTestBase` with a `TestFixtures` builder (org, company, transaction, members per role, workstream, evidence from a checked-in sample PDF).
- Migration test: apply V1..Vn to an empty DB and to a DB seeded at V9.
- Frontend: add Vitest + React Testing Library; one smoke test.
- Add Playwright project `e2e/` with a single login→create transaction test; will be extended in each phase.
- CI workflow (GitHub Actions) running backend verify, frontend lint/build/test, E2E on compose.
- *Accept:* all existing tests still pass; CI green on main.

**A2 — Authorization matrix + policy layer.** §29, §30, §52, §55 Security.
- Introduce `PolicyService` (§2.7) and route existing `PermissionService` checks through it.
- Seed `permission_grants` for all 12 `TransactionRole`s. Add permissions required later now as enum values: `FACT_ACCEPT`, `FACT_VERIFY_FINANCIAL`, `FACT_VERIFY_LEGAL`, `FACT_VERIFY_GENERAL`, `CONFLICT_RESOLVE`, `EVIDENCE_UPLOAD`, `EVIDENCE_ARCHIVE`, `DISCLOSURE_EDIT`, `REVIEW_PERFORM`, `APPROVAL_GRANT`, `COMPILE_FINAL`, `EXCEPTION_APPROVE`, `RULE_AUTHOR`, `RULE_APPROVE`, `TRANSACTION_STATE_TRANSITION`.
- Parameterized security test: every endpoint × every role × {same transaction, other transaction same org, other org} → expected status. Table lives in `src/test/resources/authz-matrix.csv` so reviewers can read it.
- ArchUnit rule from §2.7.
- *Accept:* cross-org and cross-transaction reads of evidence, facts, tasks, disclosures return 404 (not 403, to avoid existence leaks); matrix test covers 100% of controller mappings (test enumerates `RequestMappingHandlerMapping`).

**A3 — Fact semantic keys + typed values.** §4.3, §11, §15, §70.1.
- DB: `fact_definitions` catalog (seed ~40 SME-IPO keys: revenue, EBITDA, PAT, net worth, borrowings, inventory, receivables, paid-up capital, authorized capital, promoter ownership %, etc.). Add to `facts`: `lineage_id`, `fact_key`, `subject_type`, `subject_id`, `value_type` (`DECIMAL|PERCENT|MONEY|DATE|TEXT|BOOLEAN|INTEGER`), `value_decimal`, `value_date`, `value_text`, `currency`, `unit_scale` (units/lakh/crore), `accounting_basis`, `audit_status` (`AUDITED|RESTATED|UNAUDITED|PROVISIONAL|NA`), `materiality`, `sensitivity`. Backfill: `fact_key` from label via a mapping table; unmapped → `custom:{slug(label)}`; `lineage_id` from the root of the `supersedes_fact_id` chain.
- Normalize money to a canonical minor unit (paise, `NUMERIC(24,0)`) plus display scale, so ₹42.18 crore and ₹4218 lakh compare equal.
- API accepts `factKey` (from catalog) instead of free label; label becomes display.
- Candidate facts gain `proposed_fact_key` (matcher fills it deterministically where the label maps).
- *Accept:* two facts with different labels but the same key + period + subject are detected as the same fact; value comparisons are type-aware (percent vs decimal, crore vs lakh).

**A4 — Evidence versioning + custody.** §4.1, §7.1, §53.
- DB: `evidence.lineage_id`, `version`, `parent_evidence_id`, `document_date`, `effective_from/to`, `access_classification` (default `CONFIDENTIAL`), `retention_state` (`ACTIVE|ARCHIVED|LEGAL_HOLD`), `source` (`UPLOAD|REGISTRY|EMAIL|SITE_VISIT|...`).
- "Replace file" creates a new version; old row and file untouched.
- Hash computed while streaming to storage; storage path is content-addressed (`sha256/ab/cd/<hash>`) and write-once. Duplicate hash in same transaction → link, don't duplicate.
- Remove any hard delete; replace with `archive` which is blocked (422 `EVIDENCE_REFERENCED`) if any `dependency_edges`/provenance references exist.
- Evidence access (download/page image) is audited.
- *Accept:* invariant "deleted evidence cannot remain falsely represented as available" — archived evidence shows as archived everywhere it's referenced; file bytes re-hash to stored hash (scheduled integrity check job).

**A5 — Evidence quality state.** §7.3. Enum `UNREVIEWED…INVALID`, transitions via reviewer action with reason; `STALE`/`SUPERSEDED` set automatically by A4/A7. Quality independent of fact verification. *Accept:* fact with only `INSUFFICIENT` evidence cannot be verified if its definition requires primary evidence (config flag on `fact_definitions`).

**A6 — Audit hardening.** §33, invariant 15.
- `AuditService.record` joins the caller's transaction; exceptions propagate (remove the swallow).
- Add `entity_version`, `old_state_ref`, `new_state_ref`, `reason`, `correlation_id`, `source_channel`, `prev_hash`, `hash` (SHA-256 over canonical JSON of the row + `prev_hash`, per transaction chain).
- DB: revoke `UPDATE`/`DELETE` on `audit_events` from the app role; trigger rejects them.
- `GET /transactions/{id}/audit/verify` recomputes the chain; `GET .../audit/export` returns signed NDJSON.
- Correlation ID filter: generate/propagate `X-Correlation-Id`, put in MDC and events.
- *Accept:* test that a failing audit write rolls back the business change; tampering with a row makes verify fail at that row.

**A7 — Dependency graph + outbox.** §5, §6, §47–49, §70.11.
- Implement `dependency_edges` (§2.3), `DependencyGraphService` (`addEdge`, `removeEdge`, `dependentsOf(node, depth)`, `isStale(node)`), `domain_events` outbox + relay, `processed_events`.
- Migrate existing disclosure→fact label links into edges (`USES_VALUE`), and fact→evidence links (`SUPPORTED_BY`).
- Re-implement `ChangePropagationService` on top of the graph: on `FactSuperseded`/`FactVerified` it marks dependents stale via the pinned-version rule, not label matching.
- *Accept:* graph traversal unit tests (diamond, cycle, 5-deep chain, cross-transaction isolation); disclosure staleness behavior unchanged for existing E2E.

**A8 — Optimistic concurrency.** §46. `row_version` + `expectedVersion` on all fact, evidence, issue, task, disclosure mutations. *Accept:* two concurrent verifies → one 200, one 409.

### Phase B — Diligence control (§69 B, §70.3–14)

| ID | Ticket | Depends on |
|---|---|---|
| B1 | Candidate→Fact boundary hardening | A3, A7 |
| B2 | Review model | A7 |
| B3 | Claim model | A7, B2 |
| B4 | Diligence questions | B3 |
| B5 | Materiality assessment | A3 |
| B6 | Structured conflict workflow | A3, A7, B2 |
| B7 | Change-impact engine + impact set API | A7, B2, B3 |
| B8 | Evidence coverage | B3, B4, B5 |
| B9 | Blocker-derived readiness | B6, B7, B8 |
| B10 | Diligence workbench UI | B9 |
| B11 | Calculation engine | A7 |

**B1 — Candidate→Fact boundary.** §4.2, §26 Zero AI Authority, invariants 1–3.
- Accepting a candidate creates a Fact in `ACCEPTED` (new status between `DRAFT` and `VERIFIED`); `VERIFIED` is only reachable via `POST /transactions/{t}/facts/{id}/verify` by a different user from the accepter when materiality ≥ `MATERIAL` (four-eyes; configurable).
- Candidates record `extraction_run_id`, `method`, `parser_version`. New `extraction_runs` table.
- DB check constraint + service guard: a fact whose origin is a candidate cannot have `verified_by = created_by` when material.
- Candidate status `SUPERSEDED` when its evidence gets a new version.
- *Accept:* invariant test "candidate cannot directly become verified" at service, API and DB layers.

**B2 — Review model.** §4.7, §17.
- `reviews(id, transaction_id, target_type, target_version_id, reviewer_id, role, scope, decision[APPROVED_FOR_USE|CHANGES_REQUESTED|REJECTED], comments, evidence_considered[], follow_ups, created_at, invalidated_at, invalidated_by_event)`.
- Creating a review adds a `REVIEWS` edge pinned to the target version; when the target gets a new version, review is marked invalidated (reopened) by A7 propagation.
- `review_requirements` per object type × materiality × role (e.g. MATERIAL financial fact → CFO + DUE_DILIGENCE_TEAM).
- *Accept:* review of fact v1 shows as invalidated after v2 is created; "missing reviews" query returns exactly the unmet requirements.

**B3 — Claim model.** §4.4, §67. Versioned object (§2.2) with statement, type, materiality, owner, `required_evidence_spec` (list of evidence document types / fact keys), edges to facts/evidence (`SUPPORTED_BY`), review state, status (`DRAFT|SUPPORTED|INSUFFICIENT|ESTABLISHED|CHALLENGED`). Insufficiency computed deterministically from `required_evidence_spec` vs linked items. *Accept:* the §67 capex example: a claim missing vendor quotations lists exactly that gap.

**B4 — Diligence questions.** §9. Question library seeded from BSE SME guidance topics (§2 of spec: fundraising, valuation, bank statements, fund utilisation, vendors, litigation, auditor changes, working capital, net worth, leverage, RPTs, site visit). Per-transaction instances with owner, due date, severity, answer (versioned), links to facts/evidence/claims (`ANSWERS` edges), review, exceptions. *Accept:* creating a transaction of type SME_IPO instantiates the library; answering requires ≥1 evidence or fact link unless marked N/A with reason.

**B5 — Materiality.** §10. `materiality_assessments(target, level, basis[MANUAL|POLICY], policy_rule_id, assessor, reason)`; policy rules like "financial fact whose value ≥ X% of net worth → MATERIAL". Keep confidence, evidence quality, materiality as separate fields on UI and API. *Accept:* policy-derived materiality recomputes when the net-worth fact changes.

**B6 — Structured conflict workflow.** §11, §65, workflow D, invariant 8.
- New `conflicts` table (split from `issues`): `conflict_key = transactionId::fact_key::subject::period`, member fact versions, status `OPEN|RESOLVED|DISMISSED_AS_TEMPORAL`, owner role, resolution `{chosen_fact_version, rejected_versions[], reason, evidence_considered[], resolver, at}`.
- Temporal logic: two values with non-overlapping `valid_from/valid_to` are succession, not conflict (§65 51%→49%); overlapping validity with different values is conflict.
- Registry-sourced values participate as ordinary members (§25).
- Resolution never deletes; rejected facts → `REJECTED` with link to resolution.
- An open conflict on a MATERIAL/CRITICAL key blocks verification of that key and blocks compilation.
- Keep a linked `Issue` for the task/notification flow but the conflict is the source of truth.
- *Accept:* the full §65 scenario as an integration test (A=51% @30 Jun, C=49% from 1 Sep → no conflict; D=51% @30 Sep → conflict showing all sources, pages, uploaders, affected disclosures).

**B7 — Change-impact engine.** §6 (all 15 points), workflow F.
- `ImpactAnalysisService.analyze(changedNode, fromVersion, toVersion)` → `ImpactSet { changed, reason, evidence, superseded, conflictsCreated/Resolved, requirements[], controls[], claims[], disclosures[], calculations[], documents[], approvalsStale[], reviewsReopened[], people[], tasks[], readinessBefore/After }`, built purely from `dependency_edges` + review/approval rows.
- Persist each impact set (`impact_analyses`) so "what changed since last review" is answerable later.
- API: `GET /transactions/{t}/impact?node=FACT:{lineageId}&from=v1&to=v2`, plus dry-run `POST .../impact/preview` for a proposed value before saving.
- Generated tasks (idempotent, §2.5) per affected owner.
- *Accept:* §6 promoter-ownership example and §66 revenue example as integration tests asserting exact impact sets.

**B8 — Evidence coverage.** §7.4. Metrics per transaction/workstream: % material facts with verified primary evidence, facts lacking primary evidence, disclosures with only indirect support, conflicting sources, stale evidence. Pure SQL views + service. *Accept:* numbers match fixture expectations; each metric drills down to the list of objects.

**B9 — Readiness.** §14. Replace percentage-based readiness with `ReadinessEvaluator` producing state (`NOT_READY|CONDITIONALLY_READY|READY_FOR_REVIEW|READY_FOR_FILING|STALE_AFTER_CHANGE`) + list of blockers, each with type, object ref, reason, owner, next action. Recomputed async on events; snapshot persisted. *Accept:* each of the 10 readiness inputs in §14 has a test that flips the state.

**B10 — Diligence workbench UI.** §8. Replaces the transaction dashboard as the default transaction view. Answers "what prevents confident submission now?": blockers grouped by type from B9, coverage from B8, changed material facts since *my* last visit, pending reviews assigned to me, open conflicts, stale disclosures. Every row deep-links to the object with its provenance drawer. No charts beyond counts (§43 "enormous BI dashboards"). Also: a **conflict resolution view** (side-by-side sources with page-image highlight from existing spatial grounding) and an **impact set view**.
- *Accept:* Playwright test walks §65 scenario through the UI.

**B11 — Calculation engine.** §19 Calculations. `calculations(lineage_id, version, fact_key output, formula_expr, input edges pinned to fact versions)`. Deterministic evaluator using `BigDecimal` with explicit rounding (reuse/extend `RuleExpressionEvaluator` grammar, no scripting engine). Calculated facts marked `origin=CALCULATED`, never manually editable; stale on input change and auto-recalculated into a new version requiring review if material. *Accept:* EBITDA example; changing revenue produces new EBITDA version + reopened review, old version preserved.

### Phase C — Transaction domain depth (§69 C)

All entities here are versioned where material (§2.2), linked to evidence via edges, and **generate or reference facts** rather than duplicating values — the domain tables are the structured source; facts with the corresponding keys are derived views or explicitly linked. Decide per entity; default: domain row is authoritative, and a `fact_key` projection is created so disclosures/rules can reference it uniformly.

| ID | Ticket | Spec | Notes |
|---|---|---|---|
| C1 | Party & entity resolution | §24 | `parties` (person/entity), aliases, identifiers (PAN, DIN, CIN — store hashed/masked where sensitive). AI-free fuzzy match suggestions (normalized name + identifier); merges are human actions, reversible, audited. |
| C2 | People roles | §24 | promoter, promoter group, director, KMP, senior mgmt as time-bounded role assignments on parties. |
| C3 | Capital structure | §20 | securities, shareholders, ownership events (allotment, transfer, bonus, split, conversion, ESOP), positions derived by replaying events as-of date; pre-issue / post-issue (fresh issue + OFS) views; lock-in computation. Reconciles to `promoter ownership %` fact; mismatch → conflict (B6). |
| C4 | Fundraising history | §9, BSE guidance | equity issuances in lookback period, consideration, fund flow trace to bank-statement evidence. |
| C5 | Financial statements | §19 | statement → line items with period, basis, audit/restatement status, source page; each line item is a fact. Import from structured Excel as well as PDF candidates. Standard ratios as B11 calculations. |
| C6 | Related parties & RPTs | §21 | relationship (typed, dated, evidenced) + transactions (amount, period, approval). Suggested relationships (shared directors/addresses from C1) are candidates only. |
| C7 | Litigation & material contracts | §22, §23 | structured cases (court, number, parties, amount, status, exposure, counsel) linked to net-worth materiality rule; contracts (parties, dates, value, change-of-control, exclusivity). Expiry/amendment emits events → B7 impact. |
| C8 | Registry as evidence | §25 | Registry adapter interface → each fetch stored as Evidence (`source=REGISTRY`) with raw response + hash + retrieval time + freshness; values become candidates; disagreements go through B6, never auto-win. Keep mock adapter; define the real MCA connector boundary (auth, rate limits, failure states, SSRF-safe HTTP client) behind a feature flag. |

*Phase C accept (overall):* "Who owned what immediately before the issue, and after?" is answerable and reproducible from events + verified inputs, with every number clickable to evidence.

### Phase D — Regulatory engine (§69 D, §13, §57)

| ID | Ticket | Depends on |
|---|---|---|
| D1 | Requirement + RequirementVersion | A7 |
| D2 | Control + ControlEvaluation | D1, B11 |
| D3 | Regulatory snapshots (rule-set pinning) | D1 |
| D4 | Rule authoring, testing, approval workflow | D1, D2, A2 |
| D5 | Exceptions | D2, B2 |
| D6 | Transaction state machine | B9, D5 |

**D1 — Requirements as data.** §4.5, §13. Fields per spec (authority, citation, source URL, source document hash, effective-from/to, transaction/issue type, applicability expression, required facts/evidence/reviews/approvals, severity, remediation). Migrate existing `regulatory_rules` into requirement v1s with `source_url` required — rules lacking a verified source are imported with status `UNSOURCED` and **cannot** appear as regulatory truth in UI (show "internal policy" instead). Seed content must be checked against current SEBI ICDR / BSE SME sources (§74) by a named reviewer; engineers do not author regulatory content alone.

**D2 — Controls.** §4.6. Control = deterministic check bound to a requirement version; outcomes `PASS|FAIL|MISSING|NOT_APPLICABLE|NEEDS_REVIEW|EXCEPTION`. Each evaluation stores inputs (fact version ids), expression, result, and a human-readable explanation string. `EVALUATES` edges → re-evaluation on input change via B7. Wording rule (§13 boundary, invariant 18): UI/API text says "Based on configured rule X vN and current data, evaluates to FAIL" — never "compliant"/"SEBI will approve". Add a lint test scanning frontend strings for banned phrases.

**D3 — Snapshots.** Transaction pins a `regulatory_snapshot` (set of requirement version ids + effective date) at creation (Workflow A step 6). New requirement versions produce an "affected transactions" report and an opt-in upgrade action; never silent re-evaluation of historical conclusions.

**D4 — Rule governance.** §57, §30. Author ≠ approver (`RULE_AUTHOR` vs `RULE_APPROVE`). Each requirement version must include test cases (fixture fact sets → expected outcome) which run in CI and in the approval flow. Edits after first use → new version.

**D5 — Exceptions.** §39, invariant 19. Exception object (requirement/control, reason, affected state, evidence, risk class, owner, approver, expiry, mitigation). Approved exception turns a `FAIL` into `EXCEPTION` (still visible), expires automatically, invalidated if underlying inputs change.

**D6 — Transaction state machine.** §37, §38. Configurable lifecycle (default from §37) stored as data: states, transitions, preconditions (readiness state, required approvals, no open critical conflicts), required approver roles. `POST /transactions/{t}/transitions` with blocker list on refusal; override only via D5 exception. Replace `TransactionStatus` enum usage; migrate `ACTIVE`→`ACTIVE`, `CLOSED`→terminal.

### Phase E — Disclosure control (§69 E, §15–17)

| ID | Ticket | Depends on |
|---|---|---|
| E1 | Stable placeholders + DisclosureDependency | A7, A3 |
| E2 | Disclosure versions + section templates | E1 |
| E3 | Approval model + invalidation | B2, E2 |
| E4 | Fail-closed compilation + DocumentVersion | E2, E3, D2 |
| E5 | Reproducible provenance manifest | E4 |
| E6 | Structured document rendering | E4 |

**E1.** Placeholder syntax `{{fact:<fact_key>[@period]}}` and `{{calc:<key>}}`, `{{claim:<lineageId>}}`; editor inserts via picker, never free-typed labels. Parse on save → `USES_VALUE` edges. Migration rewrites existing `{{fact:Label}}` via A3 mapping; unresolved ones become lint errors, not silent blanks.

**E2.** Disclosure versions (§2.2) with section, title, template body, materiality, dependencies, reviewer, stale reason. Section templates for SME DRHP chapters (config data).

**E3.** Approvals (§4.8, §17): `approvals(target_type, target_version_id, approver, role, policy_id, granted_at, invalidated_at, invalidation_reason, invalidating_event_id)`. `APPROVES` edge pinned to the version; any upstream change invalidates via graph → `ApprovalInvalidated` event + notification + task. `approval_requirements` configurable per target type. Replaces `TransactionApprovalSignature` semantics (keep the table as historical).

**E4.** Compiler steps exactly per §15 Compilation 1–10. Fail-closed codes: `STALE_DEPENDENCY`, `UNVERIFIED_MATERIAL_FACT`, `OPEN_CRITICAL_CONFLICT`, `SUPERSEDED_FACT_REFERENCED`, `MISSING_REVIEW`, `UNRESOLVED_PLACEHOLDER`, `REQUIRED_SECTION_MISSING`, `CONTROL_FAILED`. `DRAFT` mode may compile with warnings (watermarked); `FINAL` mode refuses. Output: immutable `document_versions` (number, parent, creator, source state, content hash, dependency snapshot, approval snapshot, compilation result, lint result — §16). Deterministic: same state → byte-identical output (test).

**E5.** Extend Merkle manifest leaves to: document content hash, every fact version, evidence hash, requirement/rule versions, control evaluations, approvals, reviewers, compile timestamp, app git SHA. Add standalone verifier CLI (`tools/verify-provenance`, plain Java, no Spring) that takes an exported package and recomputes the root — the §34 "third party can reconstruct" test.

**E6.** Render DocumentVersion to PDF (and DOCX where required) from the compiled structure: numbering, tables, footnotes, cross-refs. Rendering is a pure function of DocumentVersion; nothing flows back (§35).

*Invariant tests added here:* stale disclosure cannot compile as clean; superseded fact cannot supply final output; old approval cannot approve new version.

### Phase F — Filing package & observation loop (§69 F, §18, §35–37)

| ID | Ticket |
|---|---|
| F1 | Filing package: document version + evidence index + SEBI Dec-2024 diligence-repository export + provenance manifest + approval certificate + final hash. Status is `FILING_READY`; "filed" only via explicit recorded action with external reference (§36). Referenced evidence moves to `LEGAL_HOLD` (§53). |
| F2 | Regulatory observations as first-class objects (§18 fields), linked by edges to facts, evidence, disclosures, diligence questions. Creating one generates a response task and moves the transaction to `OBSERVATIONS` (D6). |
| F3 | Response workflow: versioned draft → review → approval; approved response freezes a provenance snapshot (§68). Required fact/disclosure changes go through normal B7 impact flow. |
| F4 | Resubmission tracking: new package version with diff against the filed one (changed disclosures, facts, and why). |

*Accept:* §68 inventory example end-to-end in Playwright.

### Phase G — Intelligence (§69 G, §26–28) — only after A–F are stable

Every AI feature writes an `ai_artifacts` row (§27: provider, model, version, prompt template version, input ids + hashes, output, confidence, reviewer, decision, resulting object id) and its output enters the system **only** as a candidate/suggestion. ArchUnit rule: classes in `com.syndicate.ai` may not depend on any `*Repository` for Fact, Claim, Approval, Review, Conflict, AuditEvent — they may only call `CandidateService`/`SuggestionService`.

| ID | Ticket |
|---|---|
| G1 | `ai_artifacts` provenance + `LlmClient` abstraction (default model per current Claude model list; prompt templates versioned in repo). |
| G2 | Document classification + candidate fact/claim extraction (augments, doesn't replace, deterministic extraction; both runs recorded in `extraction_runs`). |
| G3 | Transaction search (§28): Postgres full-text first; results show object state + provenance; semantic search added later, retrieved objects remain authoritative. |
| G4 | Suggestions: conflict suggestions, related-party suggestions, missing-evidence suggestions, diligence-question suggestions, entity-resolution suggestions. |
| G5 | Drafting assist for disclosures and observation responses — drafts land as unapproved disclosure versions that still pass E4. |

Metrics for G (§58): acceptance rate of AI candidates, time upload→accepted fact. Never "AI calls".

### Phase H — Production hardening (parallel; required before first customer pilot)

| ID | Ticket | Spec |
|---|---|---|
| H1 | Object storage (S3-compatible, e.g. MinIO locally) with versioning, object lock, SSE, access logs; migrate `FileStorageService` behind an interface. | §50 |
| H2 | Auth: short-lived access tokens (≤15 min) + rotating refresh cookie (httpOnly, SameSite), email verification, account recovery, MFA (TOTP) required for approver/verifier roles, rate limiting + lockout. | §51 |
| H3 | Upload safety: size limits, magic-byte type validation, ClamAV scan before processing, sandboxed PDF/OCR workers with CPU/mem/time limits. | §50–51 |
| H4 | Security headers, CSP, dependency scanning (OWASP dep-check / npm audit) in CI, secrets via env/secret manager only. | §51 |
| H5 | Observability: Micrometer + Prometheus metrics listed in §54, structured JSON logs with correlation/transaction/object/operation IDs, dashboards + alerts for queue depth, failed extractions, audit write failures. | §54 |
| H6 | Backup/restore drills for Postgres + object storage; queue failure/replay test; documented RPO/RTO. | §56 |
| H7 | Retention & legal-hold policy engine. | §53 |
| H8 | Optional Postgres RLS on transaction-scoped tables as defense in depth. | §52 |

---

## 5. Invariant test suite (build in A1, grow each phase)

Create `backend/src/test/java/com/syndicate/invariants/`. One test class per invariant from spec §55 and §73. These are the contract of the product; a PR that breaks one is never merged, even with a "temporary" skip.

| # | Invariant | Test lands in |
|---|---|---|
| 1 | AI output is never authoritative by itself | G1 (ArchUnit + service) |
| 2 | Candidate cannot directly become verified | B1 |
| 3 | Critical facts require appropriate verification (role + four-eyes) | B1, A2 |
| 4 | Material facts never overwritten in place (DB trigger + test) | A3 |
| 5 | Evidence preserved; hash re-verifies; archived evidence never shown as available | A4 |
| 6 | Every material disclosure lists its dependencies | E1 |
| 7 | Every material fact lists its evidence | A5/B1 |
| 8 | Conflicts explicit, never auto-resolved | B6 |
| 9 | Changes propagate to dependents | A7, B7 |
| 10 | Stale disclosure cannot compile as clean | E4 |
| 11 | Old approval cannot approve new version | E3 |
| 12 | Rules versioned by source + effective date; historical snapshot retained | D1, D3 |
| 13 | Historical state reconstructable (`asOf` query equals stored snapshot) | A3, B7 |
| 14 | Permissions enforced server-side (matrix test) | A2 |
| 15 | Audit not mutable; chain verifies | A6 |
| 16 | Provenance reproducible by external verifier | E5 |
| 17 | Documents derived: deleting all DocumentVersions loses no facts | E4 |
| 18 | No "regulatory approval" wording | D2 |
| 19 | Exceptions explicit, authorized, audited, expiring | D5 |
| 20 | Every automated decision has an explanation string | B9, D2 |

### Golden end-to-end scenario (§55 E2E, 18 steps)
Maintain one Playwright spec `e2e/golden-path.spec.ts` that grows with each phase until it covers all 18 steps in §55, using the fixtures from spec §65–68 (ownership conflict, restated revenue, capex claim, inventory observation). Sample evidence PDFs live in `e2e/fixtures/` and are synthetic — never real issuer data.

---

## 6. Milestones

| Milestone | Contents | What a merchant banker can do |
|---|---|---|
| **M1 — Trustworthy core** | Phase A + B1, B6 | Upload evidence, accept and verify facts with four-eyes, see explicit conflicts with sources, trust the audit trail. |
| **M2 — Wedge** (§61) | rest of Phase B | "Upload → facts → reconcile → show evidence → map to disclosures → detect change → see everything affected." Pilot-ready with one friendly merchant banker (with H1–H5). |
| **M3 — Deep domain + rules** | Phases C, D | Cap table, RPTs, litigation, contracts; source-linked controls; state machine with exceptions. |
| **M4 — Filing-ready** | Phase E, F | Fail-closed compiled DRHP with reproducible provenance; observation/response loop. |
| **M5 — Leverage** | Phase G | AI extraction and search behind the candidate boundary. |

**Exit criterion for the whole plan** = spec §71: from one system, answer all 13 questions ("What do we know? … What transaction state produced this filing?") for the golden scenario, each answer reachable in ≤2 clicks from the workbench and backed by an automated test.

---

## 7. Product guardrails for implementers

- Before building a Phase C/D/F feature, check whether customer discovery (§60) has validated it; if the ticket is marked **[validate]** by the product owner, build the thinnest version. §70: "Do not build all of these blindly."
- Keep the four labels in docs/PRs distinct (§75): *implemented behavior / intended architecture / regulatory requirement / customer-validated pain / engineering hypothesis*.
- Do not add: chat UI, readiness percentages, compliance scores, blockchain, microservices, graph DB, autonomous agents that mutate state (§43).
- Metrics to instrument (§58): time upload→accepted fact, conflict detection→resolution, stale disclosures caught pre-submission, stale approvals caught, downstream objects auto-identified per change.
