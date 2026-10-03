# Spatial Evidence Grounding & CandidateFact Extraction Pipeline

**Date:** 2026-09-11
**Status:** Approved for planning

## 1. Goal

Implement the ingestion pipeline described in the product spec: uploaded evidence is
checksummed, spatially parsed into tokens with bounding boxes (real text-layer
parsing for born-digital PDFs, real OCR for scans/images), and run through a
deterministic candidate-fact matcher. The output is `CandidateFact` records —
never real `Fact` records — enforcing **Zero AI Authority**: no automated output
can become a `Fact` without an explicit human Accept action.

Any user with a `TransactionMembership` on the transaction that owns a workstream
can already upload evidence to it (existing behavior, unchanged). This spec adds
what happens to that evidence after upload.

## 2. Non-goals

- No changes to the landing page / dual entry point work — that is a separate,
  already-scoped piece of work, tracked independently.
- No integration with Requirements or Disclosure generation — neither exists yet
  in this prototype (see prior plan's "explicitly out of scope" list). The spec
  text's "deterministic reconciliation" path for satisfying a requirement is
  therefore not implemented; only the human-Accept path is.
- No support for file types other than PDF and common raster images (PNG/JPEG) for
  spatial parsing. Other types (e.g. `.docx`, `.eml`) are still stored and
  downloadable as Evidence exactly as today; they are marked
  `processingStatus = NOT_APPLICABLE` and produce no candidates.
- No multi-tenant/horizontally-scaled consumer concerns beyond what Spring AMQP
  gives for free (multiple consumer instances would already share the queue
  correctly; this spec doesn't add explicit multi-instance deployment tooling).
- No UI for browsing/annotating raw layout tokens directly — only matched
  candidates are surfaced.

## 3. Infrastructure addition: RabbitMQ

A new Docker container, alongside the existing `syndicate-db` (Postgres):

```
docker run -d --name syndicate-mq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

The management plugin (port 15672) gives a web UI to inspect queue depth and
dead-lettered messages, useful for demonstrating the pipeline is real.

Backend gains `spring-boot-starter-amqp` and `spring-retry`. `run.sh` is updated to
start/wait for both Postgres and RabbitMQ before starting the backend.

Why a real broker instead of Spring `@Async`: an in-memory executor's queued and
in-flight jobs are lost on JVM exit (crash, redeploy, `Ctrl+C`). A durable,
broker-backed queue persists the job (message) to disk and only removes it once
the consumer acknowledges successful completion — so a restart mid-job causes
**redelivery and reprocessing**, not data loss. This is the direct Spring
equivalent of a Celery/BullMQ-style task queue.

## 4. Data model changes

### 4.1 `Evidence` (additions)

| Column | Type | Notes |
|---|---|---|
| `file_sha256` | `VARCHAR(64)` nullable | Computed via `MessageDigest.getInstance("SHA-256")` over the uploaded bytes at store time. Nullable only to avoid a backfill migration for pre-existing rows; every new upload always sets it. |
| `processing_status` | `VARCHAR(32)` not null, default `'PENDING'` | `PENDING`, `PROCESSING`, `COMPLETE`, `FAILED`, `NOT_APPLICABLE` |
| `processing_error` | `TEXT` nullable | Populated on `FAILED` with a human-readable reason. Never silently swallowed. |

### 4.2 `CandidateFact` (new entity/table)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `workstream_id` | UUID FK | denormalized from evidence for direct query-by-workstream |
| `evidence_id` | UUID FK | source evidence |
| `label` | VARCHAR | e.g. "Revenue" |
| `value` | VARCHAR | raw matched value, e.g. "42,18,000" |
| `period` | VARCHAR nullable | e.g. "FY2026", if a period pattern was found nearby |
| `page_number` | INT | 1-indexed |
| `bbox_x`, `bbox_y`, `bbox_width`, `bbox_height` | DOUBLE PRECISION | in the same pixel space as `page_image_width`/`page_image_height` below |
| `page_image_width`, `page_image_height` | INT | dimensions of the rendered page image this bbox was computed against, so the frontend can position an overlay proportionally regardless of actual `<img>` display size |
| `source` | VARCHAR(16) | `TEXT_LAYER` or `OCR` |
| `status` | VARCHAR(16) | `PENDING`, `ACCEPTED`, `REJECTED` |
| `review_note` | TEXT nullable | reviewer's note, settable on accept or reject |
| `reviewed_by_user_id` | UUID FK nullable | |
| `reviewed_at` | TIMESTAMP nullable | |
| `resulting_fact_id` | UUID FK nullable | set when accepted |
| `created_at`, `updated_at` | via `BaseEntity` | |

The immutable spatial anchor tuple from the spec — `(document_id, file_sha256,
page_number, bounding_box)` — is realized as `(evidence_id, [Evidence.file_sha256
at read time], page_number, bbox_*)`. `file_sha256` is not duplicated onto
`CandidateFact` since `Evidence` rows are never mutated in place after upload
(no re-upload-in-place feature exists), so reading it via the `evidence_id` join
is equivalent and avoids denormalization drift.

Flyway migration `V2__evidence_grounding.sql` adds both the `evidence` columns
and the `candidate_facts` table with FKs and indexes on `workstream_id` and
`evidence_id`.

## 5. Processing pipeline (runs inside the RabbitMQ consumer)

1. Load the `Evidence` row and its stored file bytes.
2. Branch on content type:
   - **PDF**: Apache PDFBox opens the document. For each page, a custom
     `PDFTextStripper` override captures each word's bounding box (grouping
     `TextPosition` glyphs by whitespace boundaries, min/max of glyph boxes per
     word). If the extracted text for a page is below a small threshold
     (e.g. <20 alphanumeric characters), the page is treated as scanned: PDFBox's
     `PDFRenderer` rasterizes it to a PNG at 200 DPI, and that image is run through
     the OCR step below instead.
   - **Image (PNG/JPEG)**: used directly as OCR input.
   - **Anything else**: `processingStatus = NOT_APPLICABLE`, pipeline stops here.
3. **OCR step** (Tesseract 5, already installed on this host): shell out via
   `ProcessBuilder` to `tesseract <image> stdout tsv -l eng`, parse the TSV output
   (word-level rows, `conf` ≥ a minimum confidence threshold) into the same
   token+bbox shape as the text-layer path, tagging `source = OCR`.
4. **Candidate matcher** (deterministic, keyword/regex-based — not an LLM call):
   - A fixed label dictionary (Revenue, Total Revenue, Turnover, Net Profit, PAT,
     EBITDA, Total Assets, Total Liabilities, Share Capital) is matched
     case-insensitively against token sequences.
   - When a label is matched, tokens on the same line (grouped by `top` within a
     small tolerance) or the immediately following line are scanned left-to-right
     for a numeric pattern (`\d[\d,]*\.?\d*`, optional `₹`/`Rs`/`INR` prefix or
     `Cr`/`Lakh`/`Crore` suffix).
   - A period pattern (`FY\d{4}`, `\d{4}-\d{2}`) found on the same page is attached
     as `period` context (nearest match, not itself given its own bbox).
   - Each match emits one `CandidateFact`, anchored to the **value token's**
     bounding box (the number, not the label — that's what a reviewer needs to
     visually verify), `status = PENDING`.
5. For every page that produced at least one candidate, the rendered page image is
   cached to disk (reusing `FileStorageService`'s directory) as
   `{evidenceId}-page-{n}.png` — the OCR path already has this image; the
   text-layer path renders it once at this point via `PDFRenderer`, **always at
   200 DPI**, matching the DPI used for the OCR-input rendering in step 3 so both
   paths produce bboxes in the same pixel space as their cached image.

   **Coordinate space note (text-layer path only):** PDFBox `TextPosition`
   coordinates are in PDF user-space points (72/inch, origin bottom-left), not
   pixels of the eventual raster. Before storing `bbox_*`, convert:
   `scale = 200 / 72`; `pixel_x = point_x * scale`; `pixel_y = (pageHeightPt -
   point_y_top) * scale` (Y-flip, since raster images have a top-left origin).
   `page_image_width`/`page_image_height` are always the actual rendered raster's
   pixel dimensions, so this conversion is what guarantees the stored bbox lines
   up with the cached image regardless of path. The OCR path needs no conversion
   — Tesseract's TSV output is already in the input image's own pixel space.
6. `Evidence.processingStatus = COMPLETE`. On any exception during 2–5,
   `processingStatus = FAILED` with `processingError` set to the exception message
   (caught at the top level of the consumer method, never crashes the consumer
   thread).

## 6. Queue design

- Queue `evidence.extraction.queue`, durable, `PERSISTENT` delivery mode.
- Publisher: `EvidenceService.upload()` publishes `{ "evidenceId": "<uuid>" }`
  immediately after the DB row + file are committed, and returns `201` without
  waiting.
- Consumer: `@RabbitListener` on the queue, default automatic-ack-after-success
  semantics — the message is only removed from the broker once the listener
  method returns normally. An exception triggers Spring AMQP's retry
  interceptor (3 attempts, exponential backoff) before the message is routed to
  `evidence.extraction.dlq` (dead-letter queue) for operator inspection via the
  management UI, and `Evidence.processingStatus` is left as `FAILED`.
- **Crash recovery**: if the app process dies after receiving but before acking a
  message, RabbitMQ detects the lost consumer connection and redelivers the
  message to the next consumer that connects (on restart). No custom recovery
  code is needed — this is the property that replaces `@Async`.
- **Idempotency**: because redelivery can cause the same evidence to be processed
  more than once (e.g. the consumer finished DB writes but the app crashed before
  the ack reached the broker), the consumer's first step is to delete any existing
  `CandidateFact` rows for that `evidence_id` before re-running extraction. This
  makes reprocessing safe to repeat.
- **Manual reprocess**: `POST /evidence/{id}/reprocess` (any workstream member) —
  resets `processingStatus = PENDING` and re-publishes the message. Used to retry
  a `FAILED` evidence item after e.g. fixing a transient issue.

## 7. API surface (additions)

| Method | Path | Notes |
|---|---|---|
| `GET` | `/evidence/{id}/pages/{pageNumber}/image` | Streams the cached PNG for that page. Same workstream-membership access check as other evidence endpoints. 404 if not cached (page had no candidates and wasn't rendered). |
| `POST` | `/evidence/{id}/reprocess` | Re-queues extraction for a `FAILED` (or any) evidence item. |
| `GET` | `/workstreams/{id}/candidate-facts` | List, optional `?status=PENDING` filter. |
| `GET` | `/candidate-facts/{id}` | Detail including anchor. |
| `POST` | `/candidate-facts/{id}/accept` | Body: optional `{label, value, unit, period}` overrides + optional `reviewNote`. Creates a real `Fact` (`DRAFT`, version 1, evidence-linked to the source evidence), sets candidate `status = ACCEPTED`, `resultingFactId`, `reviewedByUser`, `reviewedAt`. |
| `POST` | `/candidate-facts/{id}/reject` | Body: optional `{reviewNote}`. Sets `status = REJECTED`, `reviewedByUser`, `reviewedAt`. |

`EvidenceDto` gains `fileSha256` (truncated display in UI), `processingStatus`,
`processingError`.

All new endpoints reuse the existing `WorkstreamService.requireAccess()` pattern
— no new access-control concept is introduced.

## 8. Frontend changes

- **Evidence table**: adds a status badge column (Pending/Processing/Complete/
  Failed/Not applicable) and shows a truncated SHA-256. A "Retry" button appears
  on `FAILED` rows, calling the reprocess endpoint. No live polling — the existing
  "reload the page's data" pattern (already used everywhere else) is sufficient
  for a prototype; a manual "Refresh" button on the section covers the common
  case of checking on progress.
- **New "Candidate Facts" section** on `WorkstreamDetailPage`, listing `PENDING`
  candidates. Each row expands to show:
  - The cached page image (`<img>`) with a bounding-box `<div>` overlay,
    positioned via `left/top/width/height` percentages computed from
    `bbox_* / page_image_*`, so it's correct at any rendered image size.
  - Editable label/value/period fields (pre-filled from the candidate) and
    Accept / Reject buttons.
  - Accepted/rejected candidates move out of the pending list (simple refetch
    after the action, consistent with the rest of the app).

## 9. Error handling summary

| Failure | Behavior |
|---|---|
| Corrupt/encrypted PDF | `FAILED`, `processingError` set, evidence still downloadable |
| Tesseract binary missing/errors | `FAILED`, `processingError` set (caught `IOException`/non-zero exit code) |
| Unsupported file type | `NOT_APPLICABLE`, not an error |
| Consumer throws mid-processing | Spring Retry (3x backoff) → DLQ + `FAILED` if still failing |
| App crash mid-processing | Message redelivered on restart (broker-level), idempotent reprocessing |
| Page image requested but not cached | `404`, since only pages with candidates are rendered |

## 10. Testing / verification plan

Manual, against the running stack (consistent with how the rest of this prototype
has been verified — no test framework exists yet in this codebase):

1. Upload a text-layer PDF containing a line like "Total Revenue ... 42,18,000
   FY2026" → confirm a `CandidateFact` appears with the correct bbox, `source =
   TEXT_LAYER`.
2. Upload a scanned image containing similar text → confirm OCR path produces a
   candidate with `source = OCR`.
3. Upload a `.txt` file → confirm `processingStatus = NOT_APPLICABLE`, no
   candidates, no error.
4. Start an upload, kill the backend process mid-processing (before it acks),
   restart it → confirm the job resumes and completes without duplicate
   candidates (proves the durability requirement this spec exists for).
5. Accept a candidate with an edited value → confirm a real `Fact` is created,
   linked to the source evidence, and the candidate is marked `ACCEPTED` with
   `resultingFactId` set.
6. Reject a candidate → confirm it disappears from the pending list and no Fact
   is created.
7. View the RabbitMQ management UI during a batch of uploads to confirm messages
   are genuinely flowing through a broker, not an in-memory structure.

## 11. Out of scope (explicit)

- Requirements / disclosure compilation and the "deterministic reconciliation"
  alternate path.
- OCR language packs beyond English.
- Horizontal scaling / multiple consumer instances tooling.
- Editing/deleting individual layout tokens.
- Landing page work (tracked separately).
