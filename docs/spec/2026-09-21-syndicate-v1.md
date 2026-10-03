# Syndicate v1: implementation and development-research handoff

**Repository examined:** `Desktop/syndicate`  
**Assessment date:** 21 September 2026  
**Implementation shape:** Java/Spring Boot modular monolith + React/Vite SPA + PostgreSQL + RabbitMQ + Docker Compose.  
**Scope of this report:** source code, migrations, scripts, configuration, UI, API, and tests currently in this repository. It is an implementation inventory, not a claim that the product is production-ready or legally compliant.

## 1. What this project is

Syndicate is an early implementation of infrastructure for an Indian SME IPO. Its central model is that a prospectus/DRHP is generated from a continuously verified company-and-transaction state, rather than acting as the system of record. A company belongs to an organization; a transaction is opened against that company; transaction members work in workstreams; they upload evidence, create or accept facts, verify facts, resolve issues and tasks, evaluate rules, and compile disclosures into a DRHP.

The main policy idea is **“zero AI authority.”** The system can extract structured *candidate facts* from documents, but only a human acceptance creates a fact, and only `VERIFIED` facts resolve DRHP placeholders. There is no generative-AI/model integration in the codebase.

The repository contains:

- 203 Java production files, organized by feature/package.
- 9 Java unit-test files (currently not compilable as a complete test suite; see §16).
- 49 frontend source files.
- 9 ordered Flyway migrations (`V1`–`V9`).
- Product/design material: `README.md`, the longer product specification `syndicate.md`, and a spatial-evidence design/plan in `docs/superpowers/`.
- Product screenshots, useful as visual regression/reference material, in `screenshots/`.

## 2. System topology and runtime data flow

```text
React browser (SPA, port 5173) --JWT REST/SSE--> Spring Boot API (port 8080)
                                              |                  |
                                              |                  +-- local/upload volume
                                              |
                                              +-- PostgreSQL 16 (Flyway schema)
                                              +-- RabbitMQ 3.13 durable extraction queue + DLQ

PDF/image upload -> SHA-256 -> evidence row/file -> RabbitMQ job
  -> PDFBox text layout OR Tesseract OCR -> candidate facts + cached page PNGs
  -> human accept -> draft Fact -> human verify -> rules/conflicts/DRHP compilation
```

The API and extraction consumer run in the same Spring process. The extraction job is published only after the evidence transaction commits. The listener has three retry attempts with exponential 1s/2s/4s backoff and then rejects without requeueing to the dead-letter exchange/queue. Extraction re-deliveries are idempotent at candidate level: prior candidates for that evidence are deleted before parsing again.

Browser document-processing updates are streamed per transaction through SSE (`evidence-status` events), maintained in memory for 30 minutes. The EventSource token is passed in `access_token` only for URLs ending in `/events`; the JWT filter explicitly supports that exception.

## 3. Build, deployment, and local operations

### Supported startup paths

1. **Recommended container path:** `./start.sh`
   - Checks Docker/Compose availability and Docker daemon availability.
   - Copies `.env.example` to `.env` if absent.
   - Runs `docker compose up -d --build` (currently both normal and `--rebuild` paths run the same command).
   - Waits up to 420 seconds for `GET /api/health` and the web server.
   - `--logs` follows all Compose logs; `--down` stops the stack.
2. **Local-development path:** `./run.sh`
   - Starts/reuses separately named Postgres and RabbitMQ containers, then runs `mvn spring-boot:run` and `npm run dev` on the host.
   - Logs API/UI output in `.run-logs/`; Ctrl-C stops only host app processes, not the database/broker containers.
3. **Manual path:** run Compose services `postgres rabbitmq`, then `mvn spring-boot:run` in `backend` and `npm install && npm run dev` in `frontend`.

### Container build details

- `backend/Dockerfile`: Maven 3.9/Eclipse Temurin 17 builder; `mvn dependency:go-offline`; packages with `-DskipTests`; runtime is Temurin 17 JRE with `tesseract-ocr` and `curl`; health endpoint is polled after a 45s start period.
- `frontend/Dockerfile`: Node 20 Alpine runs `npm ci` and `npm run build`; static output is served by nginx. Its nginx configuration falls unknown paths back to `index.html` so React Router deep links work.
- Compose has persistent named volumes for Postgres, RabbitMQ, and `/data/uploads`; API starts only after DB/broker healthchecks; web starts after API healthcheck.
- Compose bakes `VITE_API_BASE_URL=http://localhost:<host API port>/api` into the web image at build time. It is therefore not runtime-configurable without rebuilding the image.

### Required software outside Docker

For local development: JDK 17, Maven, Node 20, Docker/Compose for DB/broker, and Tesseract on `PATH` if OCR fallback needs to run. Maven uses Spring Boot 3.3.4. The frontend package manifest uses React 19.2.8, React Router 7.18.3, Vite 8.3.0, react-hot-toast and Oxlint; README language saying React 18 is stale.

### Configuration and operational limits

Environment settings are prefixed `SYNDICATE_`: DB host/port/name/user/password, RabbitMQ host/port/user/password, JWT secret/expiry, CORS origins, API/web ports, and upload directory. `application.yml` defaults to local Postgres/RabbitMQ, validates (does not generate) JPA schema, enables Flyway, limits request and file size to **25 MB**, and defaults JWT expiry to 86,400,000 ms (24h).

The checked-in local `.env` uses non-default ports `55432`, `55672`, `55673`, `8280`, and `5273`; it is ignored by Git. Default credentials and the dev JWT secret are intentionally unsafe for exposure. RabbitMQ management defaults to `guest`/`guest`.

## 4. Backend structure and cross-cutting behavior

`com.syndicate` is package-by-feature:

| Package | Implemented responsibility |
|---|---|
| `auth`, `user`, `config` | registration/login/JWT, BCrypt, security/CORS |
| `organization`, `company`, `transaction`, `workstream` | core deal hierarchy, memberships, lifecycle |
| `evidence`, `ingestion`, `stream`, `candidatefact` | upload/storage/checksum, asynchronous spatial extraction, SSE, review candidates |
| `fact`, `conflict`, `audit` | immutable fact versions/bitemporal reads, conflict issues, append-only event records |
| `regulatory`, `task`, `notification` | data-held rules, readiness, generated/manual Kanban tasks, in-app notifications |
| `drhp`, `provenance` | disclosures, stale propagation, compiler/lint, Merkle filing manifest |
| `registry` | pluggable external lookup interface; deterministic MCA mock and mismatch issues |
| `invitation`, `permission`, `issue` | token invites, role permissions, issue workflows |

All public error handling is centralized in `GlobalExceptionHandler` and returns an `ApiError`; services, not controller annotations, enforce most ownership/membership rules. `BaseEntity` supplies UUID identity and timestamps. Most persistence is JPA repositories and transactional services.

Authentication is stateless JWT. Public endpoints are registration, login, health, and invitation-token preview; everything else needs authentication. Passwords use BCrypt. CORS allows only the configured origins and methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`. There is no refresh-token, logout/revocation, email verification, MFA, rate limiting, account recovery, OpenAPI contract, or API versioning.

## 5. Data model and migrations

### Core tables and relationships

- `users`: unique email, BCrypt password hash, full name.
- `organizations` and unique `organization_memberships`: organization types and `OWNER`/`ADMIN`/`MEMBER` seats.
- `companies`: legal identity, optional unique CIN and PAN, office, incorporation date, constitution, owner organization.
- `transactions`: company, lead organization, type/name/status, optional estimated filing date.
- `transaction_memberships`: one user per transaction, plus organization and transaction role.
- `workstreams`: a type and optional description under a transaction.
- `facts`: workstream fact value/unit/period/status/version/supersession chain/creator/verifier and two time axes.
- `evidence`: workstream document metadata, local storage key, SHA-256, processing status/error; `fact_evidence_link` is many-to-many.
- `candidate_facts`: evidence-derived, spatially anchored candidates and their human-review/resulting-fact linkage.
- `issues`: workstream issue/severity/status/owner/due date/resolution plus related facts/evidence; it can be rule-generated or conflict-keyed.
- Invites/signatures, regulatory rules/evaluations, tasks/notifications, disclosures/DRHPs, audit events, registry snapshots, and provenance manifests are added in later migrations.

### Schema evolution

| Migration | Actual addition |
|---|---|
| V1 | foundational users/orgs/companies/transactions/workstreams/facts/evidence/issues and link tables/indexes |
| V2 | evidence checksum/status/error and spatial candidate facts |
| V3 | organization/transaction invitations and approval signatures |
| V4 | fact `valid_from`, `valid_to`, `system_superseded_at` and indexes |
| V5 | estimated filing date, DB-backed regulatory rules/evaluations, fact links, seeded SME rules |
| V6 | task engine and in-app notifications |
| V7 | disclosures, fact dependency links, compiled DRHP documents/fact links |
| V8 | audit event table and conflict-key support on issues |
| V9 | registry snapshots, DRHP compile mode/Merkle root, provenance manifests |

Postgres `pgcrypto` supplies `gen_random_uuid()`. Flyway migrations are authoritative because Hibernate is `ddl-auto: validate`; never edit an already-applied migration—add a numbered migration. There are no database-level triggers or row-level-security policies. Several “append-only/immutable” guarantees are primarily application-convention rather than database revocation/trigger enforcement.

## 6. Core product features, complete inventory

### Identity, organizations, companies, transactions, and memberships

- Registration creates a user, their organization, an `OWNER` membership, and a JWT in one flow. Login returns JWT/user. The SPA retains the token in `localStorage` as `syndicate_token` and calls `/auth/me` to restore a session.
- Organizations can be listed/created/viewed. Owners/admins can add existing registered users as members and can send/revoke organization invitations. The frontend has organization list/detail pages, member/invitation management, and invitation inbox/accept pages.
- Companies are listed only if visible via the caller's organization context. Users can create, view, and update company identity. The UI supports create/list/detail; company detail includes registry panel and transactions.
- Only `SME_IPO` exists as a transaction type. New transactions begin `DRAFT`, associate an existing company and lead organization, and make the creator a transaction member in the selected role. Current lifecycle enum: `DRAFT`, `ACTIVE`, `CLOSED`.
- Transaction membership can be listed, added (for an already registered and organization-associated email), removed, self-left, or the entire transaction can be deleted by an `ISSUER_ADMIN`. The last issuer admin cannot self-leave. Deletion has a specialized cascade service and is materially destructive.
- A `DRAFT -> ACTIVE` transition requires separate approval signatures from an `ISSUER_ADMIN` and a `LEAD_BANKER`; each signatory role can sign once per transition. Other status transitions are not explicitly gated in the shown policy. The transaction UI supports approval status/signing, membership, removal/leave, and destructive deletion.
- Workstreams can be listed/created/get/updated. Supported types: `TRANSACTION_READINESS`, `CORPORATE_SECRETARIAL`, `FINANCIAL_DUE_DILIGENCE`, `LEGAL_DUE_DILIGENCE`, `TAX`, `BUSINESS_DUE_DILIGENCE`, `REGULATORY_DUE_DILIGENCE`, `CAPITAL_STRUCTURE`, `MATERIAL_CONTRACTS`, `LITIGATION`.

### Roles, invitations, and permissions

Transaction roles: `ISSUER_ADMIN`, `PROMOTER`, `CFO`, `COMPANY_SECRETARY`, `LEAD_BANKER`, `DUE_DILIGENCE_TEAM`, `LEAD_LAWYER`, `LEGAL_ASSOCIATE`, `AUDITOR`, `TAX_ADVISOR`, `REGULATORY_CONSULTANT`, `ADVISOR`.

- Org `OWNER`/`ADMIN` can manage org members and invitations; `MEMBER` has no organization management permission.
- Issuer admin and lead banker can manage transaction members, send transaction invitations, and transition status; issuer admin also has financial-fact verification permission. Auditors can verify financial facts. Lead lawyer and legal associate have litigation-issue resolution permission.
- Organization invitations and transaction invitations use opaque 64-character-style tokens, can be individual (email) or organization-level, expire after the service’s configured constant (seven days in code), and move through `PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED`/revoked behavior. Token preview is public; acceptance/rejection requires login. Organization invite acceptance adds a membership; organization-level transaction invite must be accepted by target org owner/admin and represents organization participation rather than immediately adding all users.
- `GET /api/invitations/mine` includes individual email invites and organization invites for organizations where the caller is owner/admin.

### Evidence ingestion, extraction, and spatial provenance

- Evidence upload accepts multipart `file` plus an `EvidenceDocumentType`: audited financial statement, licence, certificate, contract, board resolution, government filing, property document, email, other.
- Original bytes are locally stored under a generated storage path; SHA-256 is calculated and saved. Current storage is filesystem/volume-backed—not object storage, virus scanned, encrypted, versioned, or retention-managed.
- PDFs use PDFBox 3 layout parsing. The parser renders pages and uses tokens from a usable text layer; pages judged to need OCR go to a shell-out Tesseract 5 TSV runner. Image uploads go directly to Tesseract after ImageIO decoding. Other MIME types become `NOT_APPLICABLE`.
- Matcher output has a label/value/period, page number, bounding box, rendered page dimensions, and source (`TEXT_LAYER` or `OCR`). Candidate values are heuristic extraction output, not AI-authoritative facts.
- Candidate status is `PENDING`, `ACCEPTED`, or `REJECTED`. A reviewer can add notes. Acceptance creates a new v1 draft Fact, links it to source evidence, and stores the resulting fact ID. Rejection never creates a fact. Candidate review interface displays a page image and bounding rectangle.
- Page PNGs are cached locally for only pages with matches, then exposed through an authenticated endpoint. Fact trace displays source file, page, extraction source, reviewer/time, a visual page anchor, all linked evidence, version, verifier, and business validity. Directly entered facts show no spatial origin.
- Evidence can be listed by workstream or entire transaction, downloaded, deleted, or manually reprocessed. Deletion removes candidate rows, page cache and stored file, then its evidence row; it does not describe a preserve-legal-record policy.
- Browser UI’s Documents panel offers upload, status/error states, reprocess and download; it subscribes to SSE to refresh live extraction progress.

### Facts, evidence links, temporal history, and conflicts

- Facts can be directly created under a workstream, fetched/listed, evidence-linked/unlinked, verified, superseded, traced, and viewed in full supersession history.
- Statuses: `DRAFT`, `VERIFIED`, `SUPERSEDED`, `REJECTED`. Superseding never updates a value in place: it marks the old fact superseded and creates a successor with incremented version and `supersedes_fact_id`. A superseded fact cannot be superseded again.
- Business-time fields are `valid_from`/optional `valid_to`; system-time is creation timestamp plus `system_superseded_at`. `?asOf=<instant>` returns versions believed current by the system at that time. It does **not** additionally filter facts by business validity at that instant; it reconstructs system belief, as implemented.
- Financial due-diligence facts may only be verified by an auditor or issuer admin. Other workstreams only require transaction/workstream access to verify.
- Fact changes write audit events, asynchronously re-evaluate readiness after commit, and run conflict detection after commit. Superseding also invalidates dependent disclosures/DRHPs.
- Conflict detection groups current (not superseded) facts by normalized label+period across a transaction. If normalized values differ, it opens/reopens one high-severity issue, links competing facts/evidence, lists sources, and deliberately chooses no winner. If values converge, it auto-resolves the keyed conflict issue.
- The workstream UI has manual Fact form, fact cards, evidence linking, candidate review, historical “as of” retrieval, verify and supersede paths, and a trace route.

### Issues, audit, notifications, and work management

- Manual issues support list/create/get/update; fields include severity, owner, due date, resolution, related facts/evidence. User-facing constants show low/medium/high/critical, while backend additionally supports `BLOCKING` for rules.
- Rules and conflict/registry detection also create issues. Financial/litigation restrictions are service-level policy; research should audit whether every issue update path consistently applies the intended litigation-resolver permission.
- Audit records store actor, transaction, action, entity, summary, old/new values, reason and time. They can be read per transaction in reverse chronological order. No API updates/deletes audit events and the entity exposes no setters. Importantly, audit recording intentionally catches/logs failures so it does not abort the primary operation—this favors availability over strict non-repudiation.
- Tasks use Kanban states `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `RESOLVED`, `UNASSIGNED_ESCALATED`. They can be manual or one idempotent generated task per rule/transaction. List filters: workstream, status, severity, assignee. Any transaction member can create/update a task; unassigned tasks cannot be moved beyond To-do; users cannot manually set escalated status.
- Default role routing maps each workstream to an expected transaction role (e.g. financial->auditor, legal/litigation/contracts->lead lawyer, capital/readiness->lead banker, regulatory->regulatory consultant, tax->tax advisor). A task is auto-assigned to a deterministic matching holder, or escalated when no one holds the role. Escalation notifies issuer admin/lead banker to invite the missing advisor.
- In-app notifications can be listed and marked read. They cover task assignment and missing-role escalation; there is no email, push, webhook, digest, notification preference, or real-time notifications stream.
- UI includes a drag/drop Kanban board, task drawer, assignment selector, resolution notes, notification visibility, an audit timeline, and manual issue form.

### Regulatory rules and readiness

Rules are data held in `regulatory_rules`, with JSON expressions evaluated by a deterministic in-house JSON-logic-like evaluator against a `RuleFactContext`; no external regulator feed/model determines a verdict.

Seeded rules:

| Code | Rule | Required data / outcome |
|---|---|---|
| `SEBI_SME_POST_ISSUE_CAPITAL` | post-issue paid-up capital <= INR 250,000,000 | one `Post-Issue Paid-Up Capital` fact |
| `SEBI_SME_EBITDA_TRACK_RECORD` | positive EBITDA in at least 2 of 3 periods | three `EBITDA` facts |
| `SEBI_SME_VALID_LICENSES` | every critical licence extends past estimated filing date | licence fact(s) + transaction estimated filing date |

Evaluation saves one row per transaction/rule and links supporting facts. Failed or missing-evidence rules open/update a blocking issue and generated routed task; passing results auto-resolve the generated issue and task. Readiness response includes rule results, blocking issues and a state: `NOT_READY`, `CONDITIONALLY_READY`, or `READY_FOR_FILING`. Users may GET readiness or POST a re-evaluation; fact verify/supersede schedules an automatic re-evaluation after commit.

There is no UI/rule CRUD endpoint for creating, changing, approving, versioning, testing, or disabling rules, despite `active`/metadata existing in the schema. Only these three rules are seeded. Research must treat the rules as illustrative product logic, not a complete or certified representation of SEBI ICDR requirements.

### Disclosures, DRHP compilation, stale propagation, and cryptographic provenance

- A disclosure has unique section code per transaction, title, free-text body template, sort index, status, stale reason, creator and explicit many-to-many fact dependency links. Statuses: `DRAFT`, `READY`, `STALE_REQUIRING_REVIEW`.
- Templates reference facts as `{{fact:Label}}`. Compilation resolves a label only when exactly one current `VERIFIED` fact case-insensitively matches. It emits lint findings for no disclosures, stale disclosures, absent/unverified labels and ambiguous verified labels. Compiled sections retain text versus fact segments, enabling value traceability.
- Compile runs against transaction disclosures and facts and refuses to create a clean output if lint fails or readiness has blocking results. It records compiled content, lint summary, version and citing fact links. UI has a DRHP panel to create/edit/link disclosures, compile draft/final, see lint results and version state.
- On a fact supersession, linked disclosures are marked stale and DRHP documents that cite the old fact become `INVALIDATED`; next compile must proceed from reviewed state. This is a one-way safety signal, not automatic prose remediation.
- `DRAFT_PREVIEW` creates a normal compiled document. `FINAL_FILING` additionally creates a provenance manifest with a SHA-256 Merkle root over canonical leaves covering cited fact versions, their evidence hashes, and approval signatures. Leaves are sorted; odd leaves are promoted rather than duplicated. The manifest stores canonical leaf list so root can be recomputed offline and includes per-leaf audit paths. Provenance endpoint/UI drawer exposes it.
- The compiler emits structured content, not a PDF/DOCX/EDGAR/SEBI filing package. “DRHP” here is persisted compiled text/segments; actual formatted document generation, filing submission, signature/certificate workflow, and long-term evidence package storage are not implemented.

### External registry verification

- `ExternalRegistryService` is a pluggable boundary. Current component is `MockMcaRegistryAdapter`, not a real MCA API/gateway. It derives reproducible test/demo values from CIN/PAN; `UNKNOWN...` returns no record.
- Verification saves an immutable-looking snapshot record with raw payload and SHA-256; it compares internal legal name, incorporation date, registered office, paid-up capital and authorized capital (the latter searched as a fact) against the external record. It uses the latest company transaction and finds/creates a corporate-secretarial workstream when a discrepancy must be tracked.
- Mismatches create/reopen high-severity, conflict-keyed issues showing both sources. Matching values auto-resolve prior mismatch issues. With no transaction, it logs a mismatch but cannot file an issue.
- Company detail UI shows current registry status and allows verification. No actual network registry client, authenticated credential management, freshness/expiry policy, registry source choice, or adverse-action workflow exists.

## 7. Full REST/SSE surface

All paths are under `/api`; protected unless noted. UUID path variables and request DTO validation are enforced by Spring/controller/service layers.

| Area | Endpoints |
|---|---|
| Health/auth | `GET /health` (public); `POST /auth/register`, `POST /auth/login` (public); `GET /auth/me` |
| Organizations | `GET,POST /organizations`; `GET /organizations/{id}`; `GET,POST /organizations/{id}/members` |
| Companies | `GET,POST /companies`; `GET,PUT /companies/{id}`; `GET /companies/{id}/transactions`; `GET /companies/{id}/registry-status`; `POST /companies/{id}/verify-registry` |
| Transactions | `GET /transactions`; `GET,PUT,DELETE /transactions/{id}`; `GET,POST /transactions/{id}/memberships`; `DELETE /transactions/{id}/memberships/{membershipId}`; `POST /transactions/{id}/leave`; `POST,GET /transactions/{id}/approval-signatures`; `GET /transactions/{id}/audit` |
| Workstreams | `GET,POST /transactions/{transactionId}/workstreams`; `GET,PUT /workstreams/{id}` |
| Evidence/SSE | `GET /transactions/{id}/evidence`; `GET /transactions/{id}/events` (SSE); `GET,POST /workstreams/{id}/evidence`; `GET,DELETE /evidence/{id}`; `GET /evidence/{id}/download`; `POST /evidence/{id}/reprocess`; `GET /evidence/{id}/pages/{pageNumber}/image` |
| Candidate facts/facts | `GET /workstreams/{id}/candidate-facts`; `GET /candidate-facts/{id}`; `POST /candidate-facts/{id}/accept`, `/reject`; `GET,POST /workstreams/{id}/facts` (query: `includeSuperseded`, `asOf`); `GET /facts/{id}`, `/history`, `/trace`; `PUT /facts/{id}`; `POST /facts/{id}/verify`, `/evidence-links`; `DELETE /facts/{id}/evidence-links/{evidenceId}` |
| Issues/tasks/notifications | `GET,POST /workstreams/{id}/issues`; `GET,PUT /issues/{id}`; `GET,POST /transactions/{id}/tasks`; `PATCH /tasks/{id}`; `GET /notifications`; `POST /notifications/{id}/read` |
| Rules | `GET /transactions/{id}/readiness`; `POST /transactions/{id}/readiness/evaluate` |
| Disclosures/DRHP | `GET,POST /transactions/{id}/disclosures`; `PUT /disclosures/{id}`; `POST /disclosures/{id}/fact-links`; `DELETE /disclosures/{id}/fact-links/{factId}`; `POST /transactions/{id}/drhp/compile?mode=DRAFT_PREVIEW|FINAL_FILING`; `GET /transactions/{id}/drhp`; `GET /drhp/versions/{id}/provenance` |
| Invitations | `POST,GET /organizations/{id}/invitations`; `DELETE /organizations/{id}/invitations/{invitationId}`; `POST,GET /transactions/{id}/invitations`; `DELETE /transactions/{id}/invitations/{invitationId}`; `GET /invitations/mine`; `GET /invitations/{token}` (public preview); `POST /invitations/{token}/accept`, `/reject` |

## 8. Frontend inventory

The SPA is plain JSX/JavaScript, no TypeScript or state/query library. It uses browser `fetch`, component-local state/effects, localStorage JWT, React Router `BrowserRouter`, `react-hot-toast`, and an application shell with responsive sidebar/topbar, breadcrumbs, command palette, route progress and dark visual system.

Routes: landing `/`; login/register; public invitation `/invite/:token`; protected home; invitations; organization list/detail; company list/detail; transaction detail; workstream detail; and fact trace. `ProtectedRoute` redirects unauthenticated visitors. No catch-all/404 route exists.

Key reusable components include `AppShell`, `CommandPalette` (keyboard-driven navigation), `DocumentsPanel`, `EvidenceUploadForm`, `CandidateFactCard`, `FactForm`, `IssueForm`, `TaskBoard`, `ReadinessPanel`, `DrhpPanel`, `ProvenanceDrawer`, `RegistryPanel`, `AuditTimeline`, and `RouteProgress`. `TransactionDetailPage` gathers workstreams, team/memberships/invites, approvals, tasks, readiness, DRHP and audit views; `WorkstreamDetailPage` gathers evidence, facts, candidates and issues.

There is no frontend test framework/test suite, no TypeScript static type checking, no accessibility test/tooling, no API schema-generated client, and no i18n. API errors are shown through local error banners/toasts. EventSource reconnect/backoff/error semantics should be reviewed before adding production realtime behavior.

## 9. Explicit architectural invariants worth preserving

1. Candidate extraction never becomes a fact without human acceptance.
2. Only verified facts may supply a DRHP placeholder.
3. Facts are versioned through successor rows rather than mutable correction.
4. System-time history is recoverable through `created_at`/`system_superseded_at`; business validity is independently recorded.
5. Superseding must propagate stale state to dependent outputs and reevaluate derived compliance.
6. Rule results must be deterministic from persisted rules/facts, not LLM output.
7. Internal/external conflict paths must surface all sources rather than pick one silently.
8. A draft-to-active transaction needs both issuer-admin and lead-banker approvals.
9. Rule-generated tasks must be idempotent and escalate missing seats to deal leads.
10. Final filing provenance must be canonical/recomputable (sorted Merkle leaves; promote odd node).
11. Audit events have no intended update/delete application path.

## 10. Product-spec coverage versus current code

`syndicate.md` is a broad vision. The repository implements the core-loop prototype above, not the full domain model envisioned there. Present: company/organization/transaction hierarchy, roles, evidence, candidate facts, bitemporal facts, workstreams/issues/tasks, limited deterministic rules, disclosures/DRHP compiler, audit/conflicts, mock registry and Merkle provenance.

Not found as implemented first-class features: company relationships/ownership graph; claims separate from facts; requirements/controls/reviews as domain objects; cap table/shareholding/promoters/related parties; contracts/litigation specifics beyond generic workstreams/issues; regulator integration; conversation/chat/email/WhatsApp ingestion; document versioning/compare/redlining; market/exchange interactions; actual SEBI filing; payment/billing; SSO/SCIM/MFA; external notifications; reporting/analytics/data exports beyond manifest; public APIs/webhooks; search; a multi-tenant operational admin plane; and production-grade workflow/rules authoring.

## 11. Security, compliance, and reliability research priorities

These are source-observed gaps or design decisions, not vulnerabilities proven by exploit testing.

- The dev default JWT secret and DB/RabbitMQ credentials must never reach a deployed environment. Implement secret management, rotation, distinct production identities and TLS.
- JWT is in localStorage, which makes XSS protection critical; there is no refresh, logout invalidation, device/session list, account verification/reset, MFA or rate limiting. Decide on session model early.
- SSE query-string JWTs can enter proxy/server access logs. The code restricts this exception to `/events`, but production should evaluate short-lived stream tokens/cookies or a proxy-safe approach.
- Evidence accepts MIME metadata and stores raw uploads locally. Add content sniffing/allowlisting, malware scanning, size/page/count/resource limits, zip-bomb/PDF hardening, encrypted object storage, access logging, lifecycle retention/legal hold, backups and disaster recovery.
- Tesseract is shell-invoked; retain strict process timeout/resource control and test hostile image/PDF inputs. PDF rendering/OCR can consume considerable CPU/RAM, yet queue concurrency, horizontal scaling, DLQ replay/ops UI and job observability are not configured in source.
- Authorization is service-centric and should receive systematic endpoint/role tests. In particular, review data visibility across organizations, manual task mutation permissions, issue-resolution role enforcement, evidence delete authority, invitation email binding, and final-filing authorization.
- “Append-only audit” is not DB-enforced and audit writes may fail without failing the business action. A regulated audit design likely needs immutable/WORM storage, chained integrity/signing, strict delivery semantics and audit completeness tests.
- The built-in MCA source is explicitly a mock. Regulatory rules are only three seeded examples; legal/research validation, versioned sources, effective dates, expert review/approval and explainability evidence are prerequisites to regulatory reliance.
- No observability stack is present: no Actuator metrics, structured audit/log correlation, tracing, alerting, broker/queue lag alarms, backup checks, error reporting or health checks beyond API liveness.

## 12. Suggested research and development sequence

1. **Stabilize engineering baseline:** repair tests (§16), add integration tests using Testcontainers/Postgres/RabbitMQ, frontend component/E2E tests, CI, dependency scanning, formatter/static checks, and API contract/OpenAPI generation.
2. **Security and tenancy review:** formally model visibility/permissions, test every role/resource combination, harden auth/session/secrets/CORS/SSE, and design evidence custody/retention/encryption.
3. **Evidence pipeline hardening:** benchmark parser/OCR throughput, add content validation/resource limits/virus scanning, job retry/DLQ operations, durable page-image strategy and object storage.
4. **Regulatory correctness:** engage qualified Indian capital-markets counsel; convert authoritative requirements into versioned testable rules with sources/effective dates; add rule administration and reproducibility/version pinning.
5. **Document/filing product:** determine required DRHP sections/templates, document output format, review/sign-off process, traceability requirements, and whether final filing demands PKI/time stamps and external submission interfaces.
6. **External data:** replace mock MCA adapter only after confirming licensed/legal data source, credentials, rate limits, provenance, data freshness, reconciliation/resolution workflow and privacy obligations.
7. **Workflow maturity:** ownership/cap-table/related parties/contracts/litigation models; cross-workstream dependencies; SLA/escalation; robust notifications; user/admin onboarding; bulk import/export and search.

## 13. Build and test status observed

| Command | Result |
|---|---|
| `frontend/npm run lint` | completes with 6 warnings: Fast Refresh mixed exports in `AuthContext`/`BreadcrumbContext`; synchronous state updates in effects in auth, command palette, companies page, route progress |
| `frontend/npm run build` | **passes**; Vite generated a production bundle (JS approx. 373 KB, gzip approx. 109 KB) |
| `backend/mvn test` | **fails at test compilation**, before tests run |

Backend failure details:

1. `CandidateFactServiceTest.java:43` instantiates `CandidateFactService` with 3 dependencies, but production constructor now requires 5: repository, fact repository, workstream service, `AuditService`, and `ConflictDetectionService`.
2. `EvidenceExtractionServiceTest.java:43` instantiates `EvidenceExtractionService` with 7 dependencies, but production constructor now requires the additional `ApplicationEventPublisher` eighth dependency.

Update the test setup/mocks to match the evolved constructors, then rerun `mvn test`. The existing tests target checksum utilities, candidate acceptance, PDF coordinates/layout, Tesseract TSV/runner, page-image cache and extraction. There are no controller/security/database/RabbitMQ/SSE/DRHP/readiness/invitation end-to-end tests in the current test tree.

## 14. Repository hygiene and noteworthy discrepancies

- Worktree already had unrelated local changes before this report: modified `run.sh` and untracked `.run-logs/`. This report does not alter them.
- This report is the added file `syndicate_v1.md`; no application implementation was changed.
- README claims React 18, but `frontend/package.json` installs React 19.2.8.
- README’s suggested `mvn -o test` is currently not viable until test constructors are repaired (and offline availability of all dependencies is assured).
- `start.sh --rebuild` currently does not behave differently from ordinary start, since both branches issue `docker compose up -d --build`.
- Frontend README is stock Vite template documentation and does not explain this application.
- README’s “first run creates `.env` from `.env.example`” is accurate for `start.sh`; `run.sh` does not do that preparation itself.

## 15. Key file map for the next coding agent

| Need | Starting files |
|---|---|
| Product rationale/invariants | `README.md`, `syndicate.md` |
| Architecture change for spatial evidence | `docs/superpowers/specs/2026-09-11-spatial-evidence-grounding-design.md`, corresponding plan |
| Local/container startup | `start.sh`, `run.sh`, `docker-compose.yml`, `.env.example` |
| Backend dependencies/config/security | `backend/pom.xml`, `src/main/resources/application.yml`, `config/SecurityConfig.java`, `auth/JwtAuthFilter.java` |
| Schema change work | `backend/src/main/resources/db/migration/` |
| Evidence pipeline | `evidence/EvidenceService.java`, `ingestion/EvidenceExtractionService.java`, `PdfLayoutParser.java`, `TesseractOcrRunner.java`, `CandidateFactMatcher.java`, `config/RabbitMqConfig.java`, `stream/EvidenceStreamController.java` |
| Fact integrity/derived changes | `fact/FactService.java`, `candidatefact/CandidateFactService.java`, `conflict/ConflictDetectionService.java`, `audit/AuditService.java` |
| Compliance/workflow | `regulatory/ReadinessService.java`, `RuleExpressionEvaluator.java`, `task/TaskService.java`, `notification/NotificationService.java` |
| Disclosure/provenance | `drhp/DrhpService.java`, `DrhpCompiler.java`, `ChangePropagationService.java`, `provenance/ProvenanceService.java`, `MerkleTree.java` |
| Registry | `registry/ExternalRegistryService.java`, `MockMcaRegistryAdapter.java`, `RegistryVerificationService.java` |
| Client routing/API | `frontend/src/App.jsx`, `frontend/src/api/`, `frontend/src/pages/`, `frontend/src/components/` |

## 16. Definition of “v1 currently works”

With Docker, the intended demonstrable flow is: register (creates organization) -> create company -> create SME IPO transaction -> create workstream -> upload PDF/image evidence -> watch processing -> accept candidate -> verify resulting fact -> evaluate readiness -> create/link disclosures -> compile DRHP -> inspect provenance for a final filing compile. Organization/transaction invitations, dual approval, manual/rule tasks, fact trace/history, audit, conflict detection and mock registry verification can be exercised around that flow.

It is an intentionally capable prototype, but it should not be treated as a completed regulatory filing system until the security, legal/rule validation, durable evidence custody, operational resilience, test coverage and actual document/filing capabilities listed above are researched and built.
