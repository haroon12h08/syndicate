# Spatial Evidence Grounding & CandidateFact Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every evidence upload gets checksummed, spatially parsed (real PDF text-layer extraction or real Tesseract OCR), and run through a deterministic matcher that emits `CandidateFact` records anchored to a bounding box on a specific page — never a real `Fact` — enforcing Zero AI Authority. A human reviews each candidate against its rendered source page (with the box drawn on it) and explicitly Accepts (creating a real `Fact`) or Rejects it.

**Architecture:** Evidence upload publishes a durable RabbitMQ message; a `@RabbitListener` consumer runs the pipeline (PDFBox → Tesseract fallback → regex/keyword matcher → cache page image → persist `CandidateFact` rows) inside one idempotent, retryable unit of work. A crashed app redelivers the message on restart — no custom recovery code. New `GET/POST` endpoints let a workstream member list, accept, or reject candidates, and view the anchored page image.

**Tech Stack:** Spring Boot 3.3.4 / Java 17 (existing), Apache PDFBox 3.0.3 (new, PDF text-layer parsing + rendering), Tesseract 5.5.3 CLI (already installed on this host, invoked via `ProcessBuilder`), RabbitMQ 3 via Docker + Spring AMQP + Spring Retry (new), PostgreSQL/Flyway (existing), JUnit 5 + Mockito via `spring-boot-starter-test` (already a dependency, first real use in this repo).

**Spec:** `docs/superpowers/specs/2026-09-11-spatial-evidence-grounding-design.md`

## Global Constraints

- Render DPI for every PDF page is fixed at **200** everywhere in this feature (`PdfCoordinateConverter.RENDER_DPI`) — this is what keeps stored bounding boxes aligned with the cached page image; never introduce a second DPI value.
- RabbitMQ messages for extraction jobs are published with **`MessageDeliveryMode.PERSISTENT`** explicitly set, to a **durable** queue — this is the actual mechanism that survives a broker restart, not just an app restart.
- `CandidateFact` rows are **never** auto-promoted to `Fact`. The only code path that creates a `Fact` from a candidate is `CandidateFactService.accept()`, which requires an authenticated, workstream-scoped human caller.
- Extraction is **idempotent**: `EvidenceExtractionService.process()` always deletes any existing `CandidateFact` rows for that evidence before re-running, so redelivery after a crash never produces duplicates.
- Every new backend package follows the existing package-by-feature convention (entity/repository/service/controller/dto siblings in one package) and every `@Service` that reads lazy JPA associations is annotated `@Transactional` at the class level (this codebase hit a real `LazyInitializationException` bug from skipping this — don't repeat it).
- No new frontend test framework — this repo's established pattern (see prior work) is `oxlint` + manual browser verification. Backend gets real JUnit 5 tests for pure logic and Mockito-mocked service logic; broker/OCR/crash-recovery behavior is verified manually against the real running stack, matching how the rest of this prototype was verified (documented in the spec's own testing section).
- All file/network I/O in new code must fail loudly (`FAILED` status + a real error message) — never swallow an exception silently.

---

### Task 1: Evidence schema, checksum, and byte-based storage

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__evidence_grounding.sql`
- Create: `backend/src/main/java/com/syndicate/evidence/ProcessingStatus.java`
- Create: `backend/src/main/java/com/syndicate/common/ChecksumUtil.java`
- Create: `backend/src/test/java/com/syndicate/common/ChecksumUtilTest.java`
- Modify: `backend/src/main/java/com/syndicate/evidence/Evidence.java`
- Modify: `backend/src/main/java/com/syndicate/evidence/FileStorageService.java`
- Modify: `backend/src/main/java/com/syndicate/evidence/EvidenceService.java`
- Modify: `backend/src/main/java/com/syndicate/evidence/dto/EvidenceDto.java`

**Interfaces:**
- Produces: `ChecksumUtil.sha256Hex(byte[])`, `ProcessingStatus` enum (`PENDING, PROCESSING, COMPLETE, FAILED, NOT_APPLICABLE`), `Evidence.getFileSha256()/getProcessingStatus()/getProcessingError()/setProcessingStatus(ProcessingStatus)/setProcessingError(String)`, `FileStorageService.store(byte[] content, String originalFilename)`, `FileStorageService.readBytes(String storagePath)`.

- [ ] **Step 1: Write the failing test for the checksum utility**

```java
package com.syndicate.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumUtilTest {

    @Test
    void hashesEmptyInputToKnownVector() {
        assertThat(ChecksumUtil.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85");
    }

    @Test
    void hashesKnownNistTestVector() {
        assertThat(ChecksumUtil.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sameInputAlwaysProducesSameHash() {
        byte[] content = "syndicate".getBytes(StandardCharsets.UTF_8);
        assertThat(ChecksumUtil.sha256Hex(content)).isEqualTo(ChecksumUtil.sha256Hex(content));
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        assertThat(ChecksumUtil.sha256Hex("a".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(ChecksumUtil.sha256Hex("b".getBytes(StandardCharsets.UTF_8)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails (class doesn't exist yet)**

Run: `cd backend && mvn -q -o test -Dtest=ChecksumUtilTest`
Expected: FAIL — compilation error, `ChecksumUtil` does not exist.

- [ ] **Step 3: Create `ChecksumUtil`**

```java
package com.syndicate.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ChecksumUtil {

    private ChecksumUtil() {
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=ChecksumUtilTest`
Expected: PASS (4 tests green)

- [ ] **Step 5: Create the `ProcessingStatus` enum**

```java
package com.syndicate.evidence;

public enum ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETE,
    FAILED,
    NOT_APPLICABLE
}
```

- [ ] **Step 6: Write the Flyway migration**

```sql
ALTER TABLE evidence ADD COLUMN file_sha256 VARCHAR(64);
ALTER TABLE evidence ADD COLUMN processing_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';
ALTER TABLE evidence ADD COLUMN processing_error TEXT;

CREATE TABLE candidate_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workstream_id UUID NOT NULL REFERENCES workstreams(id),
    evidence_id UUID NOT NULL REFERENCES evidence(id),
    label VARCHAR(255) NOT NULL,
    value VARCHAR(1024) NOT NULL,
    period VARCHAR(64),
    page_number INTEGER NOT NULL,
    bbox_x DOUBLE PRECISION NOT NULL,
    bbox_y DOUBLE PRECISION NOT NULL,
    bbox_width DOUBLE PRECISION NOT NULL,
    bbox_height DOUBLE PRECISION NOT NULL,
    page_image_width INTEGER NOT NULL,
    page_image_height INTEGER NOT NULL,
    source VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    review_note TEXT,
    reviewed_by_user_id UUID REFERENCES users(id),
    reviewed_at TIMESTAMP,
    resulting_fact_id UUID REFERENCES facts(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_candidate_facts_workstream ON candidate_facts(workstream_id);
CREATE INDEX idx_candidate_facts_evidence ON candidate_facts(evidence_id);
CREATE INDEX idx_candidate_facts_status ON candidate_facts(workstream_id, status);
```

(The `candidate_facts` table is created now, alongside the `evidence` column changes, even though the Java entity for it doesn't exist until Task 6 — Hibernate only validates entities that exist, so this is safe, and it keeps both pieces of this migration's schema together in one file.)

- [ ] **Step 7: Add the new fields to `Evidence`**

Modify `backend/src/main/java/com/syndicate/evidence/Evidence.java`: add imports `jakarta.persistence.EnumType`, `jakarta.persistence.Enumerated` (already imported), and these fields/methods (insert after the existing `uploadedAt` field, before the `facts` field):

```java
    @Column(name = "file_sha256")
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus;

    @Column(name = "processing_error", columnDefinition = "TEXT")
    private String processingError;
```

Change the constructor signature to accept the checksum and default the status:

```java
    public Evidence(Workstream workstream, String fileName, String storagePath, String contentType,
                     long fileSizeBytes, EvidenceDocumentType documentType, User uploadedByUser, String fileSha256) {
        this.workstream = workstream;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.documentType = documentType;
        this.uploadedByUser = uploadedByUser;
        this.uploadedAt = Instant.now();
        this.fileSha256 = fileSha256;
        this.processingStatus = ProcessingStatus.PENDING;
    }
```

Add getters/setters (anywhere in the getter block):

```java
    public String getFileSha256() {
        return fileSha256;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }
```

- [ ] **Step 8: Change `FileStorageService` to work on bytes instead of `MultipartFile`, and add `readBytes`**

Replace the `store` method and add `readBytes` in `backend/src/main/java/com/syndicate/evidence/FileStorageService.java`:

```java
    public String store(byte[] content, String originalFilename) {
        if (content.length == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }
        String originalName = Path.of(originalFilename == null ? "file" : originalFilename)
                .getFileName().toString();
        String storedName = UUID.randomUUID() + "-" + originalName;
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid file name");
        }
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        return storedName;
    }

    public byte[] readBytes(String storagePath) {
        Path file = uploadRoot.resolve(storagePath).normalize();
        if (!file.startsWith(uploadRoot)) {
            throw new BadRequestException("Invalid storage path");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored file: " + storagePath, e);
        }
    }
```

Remove the now-unused `import org.springframework.web.multipart.MultipartFile;` line if no other method in the file still uses `MultipartFile` (it won't, after this change — `load` and `delete` are untouched and don't reference it).

- [ ] **Step 9: Wire the checksum + byte storage into `EvidenceService.upload()`**

Modify `backend/src/main/java/com/syndicate/evidence/EvidenceService.java`. Add imports `com.syndicate.common.BadRequestException`, `com.syndicate.common.ChecksumUtil`, `java.io.IOException`. Replace the `upload` method body:

```java
    @Transactional
    public EvidenceDto upload(UUID workstreamId, MultipartFile file, EvidenceDocumentType documentType, User caller) {
        workstreamService.requireAccess(workstreamId, caller.getId());
        Workstream workstream = workstreamService.findWorkstream(workstreamId);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read uploaded file");
        }
        String originalFilename = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String storedName = fileStorageService.store(content, originalFilename);
        String sha256 = ChecksumUtil.sha256Hex(content);
        Evidence evidence = new Evidence(
                workstream,
                originalFilename,
                storedName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(),
                documentType,
                caller,
                sha256
        );
        return EvidenceDto.from(evidenceRepository.save(evidence));
    }
```

(This does not yet publish an extraction job — that wiring is added in Task 8 once the publisher exists. Right now every uploaded evidence row will just sit at `processingStatus = PENDING` forever, which is fine as an intermediate state for this task.)

- [ ] **Step 10: Add the new fields to `EvidenceDto`**

Modify `backend/src/main/java/com/syndicate/evidence/dto/EvidenceDto.java`:

```java
package com.syndicate.evidence.dto;

import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceDocumentType;
import com.syndicate.evidence.ProcessingStatus;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record EvidenceDto(
        UUID id,
        UUID workstreamId,
        String fileName,
        String contentType,
        long fileSizeBytes,
        EvidenceDocumentType documentType,
        UserDto uploadedBy,
        Instant uploadedAt,
        String fileSha256,
        ProcessingStatus processingStatus,
        String processingError
) {
    public static EvidenceDto from(Evidence evidence) {
        return new EvidenceDto(
                evidence.getId(),
                evidence.getWorkstream().getId(),
                evidence.getFileName(),
                evidence.getContentType(),
                evidence.getFileSizeBytes(),
                evidence.getDocumentType(),
                UserDto.from(evidence.getUploadedByUser()),
                evidence.getUploadedAt(),
                evidence.getFileSha256(),
                evidence.getProcessingStatus(),
                evidence.getProcessingError()
        );
    }
}
```

- [ ] **Step 11: Boot the app and verify via curl**

Run (from repo root): `./run.sh` (or if Postgres/backend are already running from before, just restart the backend: `cd backend && mvn -q -o spring-boot:run`).

Expected in the logs: Flyway reports `Migrating schema "public" to version "2 - evidence grounding"` and the app starts with no Hibernate validation errors.

Then, using a token from an existing user (register one if needed via `POST /api/auth/register`) and an existing workstream id:

```bash
curl -s -F file=@/tmp/test.txt -F documentType=OTHER \
  http://localhost:8080/api/workstreams/<workstreamId>/evidence \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `201`, response JSON includes a real 64-character `fileSha256` and `"processingStatus":"PENDING"`.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__evidence_grounding.sql \
        backend/src/main/java/com/syndicate/evidence/ProcessingStatus.java \
        backend/src/main/java/com/syndicate/common/ChecksumUtil.java \
        backend/src/test/java/com/syndicate/common/ChecksumUtilTest.java \
        backend/src/main/java/com/syndicate/evidence/Evidence.java \
        backend/src/main/java/com/syndicate/evidence/FileStorageService.java \
        backend/src/main/java/com/syndicate/evidence/EvidenceService.java \
        backend/src/main/java/com/syndicate/evidence/dto/EvidenceDto.java
git commit -m "feat(evidence): compute SHA-256 checksum and track processing status"
```

---

### Task 2: Pure extraction primitives — DocumentToken, coordinate conversion, candidate matcher

**Files:**
- Create: `backend/src/main/java/com/syndicate/candidatefact/CandidateFactSource.java`
- Create: `backend/src/main/java/com/syndicate/candidatefact/CandidateFactStatus.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/DocumentToken.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/PdfCoordinateConverter.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/MatchedCandidate.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/CandidateFactMatcher.java`
- Create: `backend/src/test/java/com/syndicate/ingestion/PdfCoordinateConverterTest.java`
- Create: `backend/src/test/java/com/syndicate/ingestion/CandidateFactMatcherTest.java`

**Interfaces:**
- Consumes: nothing (pure, dependency-free logic).
- Produces: `DocumentToken(String text, int pageNumber, double x, double y, double width, double height, int pageImageWidth, int pageImageHeight, CandidateFactSource source)`; `PdfCoordinateConverter.RENDER_DPI` (int, 200), `PdfCoordinateConverter.toPixelX(double)`, `PdfCoordinateConverter.toPixelY(double)`; `MatchedCandidate(String label, String value, String period, int pageNumber, double bboxX, double bboxY, double bboxWidth, double bboxHeight, int pageImageWidth, int pageImageHeight, CandidateFactSource source)`; `new CandidateFactMatcher().match(List<DocumentToken>) -> List<MatchedCandidate>`.

- [ ] **Step 1: Create the two small enums**

```java
package com.syndicate.candidatefact;

public enum CandidateFactSource {
    TEXT_LAYER,
    OCR
}
```

```java
package com.syndicate.candidatefact;

public enum CandidateFactStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
```

- [ ] **Step 2: Create `DocumentToken` and `MatchedCandidate`**

```java
package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;

public record DocumentToken(
        String text,
        int pageNumber,
        double x,
        double y,
        double width,
        double height,
        int pageImageWidth,
        int pageImageHeight,
        CandidateFactSource source
) {
}
```

```java
package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;

public record MatchedCandidate(
        String label,
        String value,
        String period,
        int pageNumber,
        double bboxX,
        double bboxY,
        double bboxWidth,
        double bboxHeight,
        int pageImageWidth,
        int pageImageHeight,
        CandidateFactSource source
) {
}
```

- [ ] **Step 3: Write the failing test for `PdfCoordinateConverter`**

```java
package com.syndicate.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PdfCoordinateConverterTest {

    @Test
    void scalesPointsToTwoHundredDpiPixels() {
        // 1 inch = 72 points = 200 pixels at 200 DPI
        assertThat(PdfCoordinateConverter.toPixelX(72.0)).isCloseTo(200.0, within(0.01));
        assertThat(PdfCoordinateConverter.toPixelY(72.0)).isCloseTo(200.0, within(0.01));
    }

    @Test
    void zeroStaysZero() {
        assertThat(PdfCoordinateConverter.toPixelX(0.0)).isZero();
        assertThat(PdfCoordinateConverter.toPixelY(0.0)).isZero();
    }

    @Test
    void renderDpiIsTwoHundred() {
        assertThat(PdfCoordinateConverter.RENDER_DPI).isEqualTo(200);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=PdfCoordinateConverterTest`
Expected: FAIL — `PdfCoordinateConverter` does not exist.

- [ ] **Step 5: Implement `PdfCoordinateConverter`**

```java
package com.syndicate.ingestion;

public final class PdfCoordinateConverter {

    public static final int RENDER_DPI = 200;
    private static final double POINTS_PER_INCH = 72.0;
    private static final double SCALE = RENDER_DPI / POINTS_PER_INCH;

    private PdfCoordinateConverter() {
    }

    public static double toPixelX(double pointValue) {
        return pointValue * SCALE;
    }

    public static double toPixelY(double pointValue) {
        return pointValue * SCALE;
    }
}
```

(`toPixelX` and `toPixelY` are identical right now — both are a pure DPI scale. Task 3's test will determine empirically whether PDFBox's extracted Y coordinate also needs a vertical flip to match the rendered image's top-left origin; if so, `toPixelY` gets a one-line change then. Keeping them as separate methods now, even though identical, means that fix stays localized to one method.)

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=PdfCoordinateConverterTest`
Expected: PASS

- [ ] **Step 7: Write the failing test for `CandidateFactMatcher`**

```java
package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFactMatcherTest {

    private final CandidateFactMatcher matcher = new CandidateFactMatcher();

    @Test
    void matchesLabelAndNumberOnSameLine() {
        List<DocumentToken> tokens = List.of(
                token("Total", 1, 10, 100),
                token("Revenue", 1, 60, 100),
                token("42,18,000", 1, 130, 100),
                token("FY2026", 1, 10, 50)
        );

        List<MatchedCandidate> results = matcher.match(tokens);

        assertThat(results).hasSize(1);
        MatchedCandidate candidate = results.get(0);
        assertThat(candidate.label()).isEqualTo("Total Revenue");
        assertThat(candidate.value()).isEqualTo("42,18,000");
        assertThat(candidate.period()).isEqualTo("FY2026");
        assertThat(candidate.bboxX()).isEqualTo(130);
        assertThat(candidate.pageNumber()).isEqualTo(1);
    }

    @Test
    void matchesNumberOnFollowingLineWhenNotOnSameLine() {
        List<DocumentToken> tokens = List.of(
                token("EBITDA", 1, 10, 200),
                token("15,00,000", 1, 10, 180)
        );

        List<MatchedCandidate> results = matcher.match(tokens);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).label()).isEqualTo("EBITDA");
        assertThat(results.get(0).value()).isEqualTo("15,00,000");
    }

    @Test
    void returnsEmptyWhenNoKnownLabelPresent() {
        List<DocumentToken> tokens = List.of(
                token("Random", 1, 10, 100),
                token("Text", 1, 60, 100)
        );

        assertThat(matcher.match(tokens)).isEmpty();
    }

    @Test
    void returnsEmptyForEmptyInput() {
        assertThat(matcher.match(List.of())).isEmpty();
    }

    @Test
    void handlesMultiplePagesIndependently() {
        List<DocumentToken> tokens = List.of(
                token("Revenue", 1, 10, 100),
                token("100", 1, 60, 100),
                token("Revenue", 2, 10, 100),
                token("200", 2, 60, 100)
        );

        List<MatchedCandidate> results = matcher.match(tokens);

        assertThat(results).hasSize(2);
        assertThat(results).anySatisfy(c -> {
            assertThat(c.pageNumber()).isEqualTo(1);
            assertThat(c.value()).isEqualTo("100");
        });
        assertThat(results).anySatisfy(c -> {
            assertThat(c.pageNumber()).isEqualTo(2);
            assertThat(c.value()).isEqualTo("200");
        });
    }

    private DocumentToken token(String text, int page, double x, double y) {
        return new DocumentToken(text, page, x, y, 40, 12, 1000, 1400, CandidateFactSource.TEXT_LAYER);
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=CandidateFactMatcherTest`
Expected: FAIL — `CandidateFactMatcher` does not exist.

- [ ] **Step 9: Implement `CandidateFactMatcher`**

```java
package com.syndicate.ingestion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CandidateFactMatcher {

    private static final List<String> LABELS = List.of(
            "Total Revenue", "Revenue", "Turnover", "Net Profit", "PAT",
            "EBITDA", "Total Assets", "Total Liabilities", "Share Capital"
    );

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(?:₹|Rs\\.?|INR)?\\s*\\d[\\d,]*\\.?\\d*\\s*(?:Cr|Crore|Lakh|Lakhs)?",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("FY\\s?\\d{4}|\\d{4}-\\d{2}", Pattern.CASE_INSENSITIVE);

    private static final double LINE_TOLERANCE = 5.0;

    public List<MatchedCandidate> match(List<DocumentToken> tokens) {
        List<MatchedCandidate> results = new ArrayList<>();
        if (tokens.isEmpty()) {
            return results;
        }

        var tokensByPage = tokens.stream().collect(Collectors.groupingBy(DocumentToken::pageNumber));

        for (var entry : tokensByPage.entrySet()) {
            String period = findPeriod(entry.getValue());
            List<List<DocumentToken>> lines = groupIntoLines(entry.getValue());

            for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
                List<DocumentToken> line = lines.get(lineIdx);
                String lineText = line.stream().map(DocumentToken::text).collect(Collectors.joining(" "));

                for (String label : LABELS) {
                    if (!containsLabel(lineText, label)) {
                        continue;
                    }
                    DocumentToken valueToken = findNumberToken(line);
                    if (valueToken == null && lineIdx + 1 < lines.size()) {
                        valueToken = findNumberToken(lines.get(lineIdx + 1));
                    }
                    if (valueToken != null) {
                        results.add(new MatchedCandidate(
                                label, valueToken.text(), period, valueToken.pageNumber(),
                                valueToken.x(), valueToken.y(), valueToken.width(), valueToken.height(),
                                valueToken.pageImageWidth(), valueToken.pageImageHeight(), valueToken.source()));
                    }
                    break;
                }
            }
        }
        return results;
    }

    private boolean containsLabel(String lineText, String label) {
        return lineText.toLowerCase().contains(label.toLowerCase());
    }

    private DocumentToken findNumberToken(List<DocumentToken> line) {
        for (DocumentToken token : line) {
            Matcher m = NUMBER_PATTERN.matcher(token.text());
            if (m.matches() && token.text().chars().anyMatch(Character::isDigit)) {
                return token;
            }
        }
        return null;
    }

    private String findPeriod(List<DocumentToken> pageTokens) {
        for (DocumentToken token : pageTokens) {
            Matcher m = PERIOD_PATTERN.matcher(token.text());
            if (m.find()) {
                return m.group();
            }
        }
        return null;
    }

    private List<List<DocumentToken>> groupIntoLines(List<DocumentToken> pageTokens) {
        List<DocumentToken> sorted = pageTokens.stream()
                .sorted(Comparator.comparingDouble(DocumentToken::y).thenComparingDouble(DocumentToken::x))
                .toList();

        List<List<DocumentToken>> lines = new ArrayList<>();
        List<DocumentToken> current = new ArrayList<>();
        double currentLineY = 0;
        for (DocumentToken token : sorted) {
            if (current.isEmpty()) {
                current.add(token);
                currentLineY = token.y();
            } else if (Math.abs(token.y() - currentLineY) <= LINE_TOLERANCE) {
                current.add(token);
            } else {
                lines.add(current);
                current = new ArrayList<>();
                current.add(token);
                currentLineY = token.y();
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=CandidateFactMatcherTest`
Expected: PASS (5 tests green)

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/syndicate/candidatefact/CandidateFactSource.java \
        backend/src/main/java/com/syndicate/candidatefact/CandidateFactStatus.java \
        backend/src/main/java/com/syndicate/ingestion/DocumentToken.java \
        backend/src/main/java/com/syndicate/ingestion/PdfCoordinateConverter.java \
        backend/src/main/java/com/syndicate/ingestion/MatchedCandidate.java \
        backend/src/main/java/com/syndicate/ingestion/CandidateFactMatcher.java \
        backend/src/test/java/com/syndicate/ingestion/PdfCoordinateConverterTest.java \
        backend/src/test/java/com/syndicate/ingestion/CandidateFactMatcherTest.java
git commit -m "feat(ingestion): deterministic candidate-fact matcher and coordinate conversion"
```

---

### Task 3: PDFBox layout parser (text-layer extraction + page rendering)

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/syndicate/ingestion/TokenCollectingStripper.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/PdfLayoutParser.java`
- Create: `backend/src/test/java/com/syndicate/ingestion/PdfLayoutParserTest.java`

**Interfaces:**
- Consumes: `DocumentToken`, `PdfCoordinateConverter` (Task 2).
- Produces: `new PdfLayoutParser().parse(byte[] pdfBytes) -> List<PdfLayoutParser.PageResult>`, where `PageResult` has public final fields `pageNumber` (int), `tokens` (`List<DocumentToken>`, empty when `needsOcr`), `renderedPng` (`byte[]`, always populated), `imageWidth`/`imageHeight` (int), `needsOcr` (boolean).

- [ ] **Step 1: Add the PDFBox dependency**

Modify `backend/pom.xml`, inside `<dependencies>`, after the flyway dependencies:

```xml
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.3</version>
        </dependency>
```

- [ ] **Step 2: Write the failing test**

This generates a real one-page PDF with two lines of text at known vertical positions (near the top and near the bottom of the page) and asserts: the text comes back correctly, `needsOcr` is `false` (there's a real text layer), the image is rendered at 200 DPI, and — critically — the token drawn near the top of the page ends up with a smaller pixel-Y than the token drawn near the bottom, proving whichever coordinate conversion is used produces the correct visual ordering.

```java
package com.syndicate.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PdfLayoutParserTest {

    private final PdfLayoutParser parser = new PdfLayoutParser();

    @Test
    void extractsTextLayerTokensWithCorrectVerticalOrdering() throws Exception {
        byte[] pdfBytes = buildTestPdf();

        List<PdfLayoutParser.PageResult> pages = parser.parse(pdfBytes);

        assertThat(pages).hasSize(1);
        PdfLayoutParser.PageResult page = pages.get(0);
        assertThat(page.needsOcr).isFalse();
        assertThat(page.renderedPng).isNotEmpty();
        assertThat(page.imageWidth).isGreaterThan(0);
        assertThat(page.imageHeight).isGreaterThan(0);

        Optional<DocumentToken> topToken = page.tokens.stream()
                .filter(t -> t.text().contains("TOPTEXT")).findFirst();
        Optional<DocumentToken> bottomToken = page.tokens.stream()
                .filter(t -> t.text().contains("BOTTOMTEXT")).findFirst();
        assertThat(topToken).isPresent();
        assertThat(bottomToken).isPresent();

        // TOPTEXT was drawn near the top of the page (visually) and BOTTOMTEXT
        // near the bottom; in image/pixel space, top-of-page must be the smaller Y.
        assertThat(topToken.get().y()).isLessThan(bottomToken.get().y());
        assertThat(topToken.get().y()).isLessThan(page.imageHeight / 2.0);
        assertThat(bottomToken.get().y()).isGreaterThan(page.imageHeight / 2.0);

        // both tokens are within the rendered image bounds
        assertThat(topToken.get().x()).isBetween(0.0, (double) page.imageWidth);
        assertThat(topToken.get().y()).isBetween(0.0, (double) page.imageHeight);
    }

    @Test
    void flagsPageAsNeedingOcrWhenTextLayerIsSparse() throws Exception {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            // no text drawn at all -> simulates a scanned page with no text layer
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdfBytes = out.toByteArray();
        }

        List<PdfLayoutParser.PageResult> pages = parser.parse(pdfBytes);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).needsOcr).isTrue();
        assertThat(pages.get(0).tokens).isEmpty();
        assertThat(pages.get(0).renderedPng).isNotEmpty();
    }

    private byte[] buildTestPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER); // 612 x 792 pt
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                stream.beginText();
                stream.setFont(font, 18);
                stream.newLineAtOffset(50, 700); // near the top (PDF y grows upward)
                stream.showText("TOPTEXT 42,18,000 FY2026");
                stream.endText();

                stream.beginText();
                stream.setFont(font, 18);
                stream.newLineAtOffset(50, 80); // near the bottom
                stream.showText("BOTTOMTEXT");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=PdfLayoutParserTest`
Expected: FAIL — `PdfLayoutParser` does not exist.

- [ ] **Step 4: Implement `TokenCollectingStripper`**

```java
package com.syndicate.ingestion;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class TokenCollectingStripper extends PDFTextStripper {

    record RawToken(String text, float x, float y, float width, float height) {
    }

    private final List<RawToken> tokens = new ArrayList<>();

    TokenCollectingStripper() throws IOException {
        setSortByPosition(true);
    }

    List<RawToken> getTokens() {
        return tokens;
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        StringBuilder word = new StringBuilder();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;

        for (TextPosition tp : textPositions) {
            if (tp.getUnicode() == null || tp.getUnicode().isBlank()) {
                flushWord(word, minX, minY, maxX, maxY);
                word.setLength(0);
                minX = Float.MAX_VALUE;
                minY = Float.MAX_VALUE;
                maxX = Float.MIN_VALUE;
                maxY = Float.MIN_VALUE;
                continue;
            }
            word.append(tp.getUnicode());
            minX = Math.min(minX, tp.getX());
            minY = Math.min(minY, tp.getY());
            maxX = Math.max(maxX, tp.getX() + tp.getWidth());
            maxY = Math.max(maxY, tp.getY());
        }
        flushWord(word, minX, minY, maxX, maxY);
    }

    private void flushWord(StringBuilder word, float minX, float minY, float maxX, float maxY) {
        if (word.length() > 0) {
            tokens.add(new RawToken(word.toString(), minX, minY, Math.max(maxX - minX, 1f), Math.max(maxY - minY, 1f)));
        }
    }
}
```

- [ ] **Step 5: Implement `PdfLayoutParser`**

```java
package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfLayoutParser {

    private static final int MIN_TEXT_LAYER_CHARS = 20;

    public static final class PageResult {
        public final int pageNumber;
        public final List<DocumentToken> tokens;
        public final byte[] renderedPng;
        public final int imageWidth;
        public final int imageHeight;
        public final boolean needsOcr;

        PageResult(int pageNumber, List<DocumentToken> tokens, byte[] renderedPng,
                   int imageWidth, int imageHeight, boolean needsOcr) {
            this.pageNumber = pageNumber;
            this.tokens = tokens;
            this.renderedPng = renderedPng;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.needsOcr = needsOcr;
        }
    }

    public List<PageResult> parse(byte[] pdfBytes) throws IOException {
        List<PageResult> results = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                int pageNumber = i + 1;

                TokenCollectingStripper stripper = new TokenCollectingStripper();
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                stripper.getText(document);

                BufferedImage image = renderer.renderImageWithDPI(i, PdfCoordinateConverter.RENDER_DPI);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                byte[] renderedPng = out.toByteArray();

                int totalChars = stripper.getTokens().stream().mapToInt(t -> t.text().length()).sum();
                boolean needsOcr = totalChars < MIN_TEXT_LAYER_CHARS;

                List<DocumentToken> pixelTokens = needsOcr ? List.of() : stripper.getTokens().stream()
                        .map(t -> new DocumentToken(
                                t.text(), pageNumber,
                                PdfCoordinateConverter.toPixelX(t.x()),
                                PdfCoordinateConverter.toPixelY(t.y()),
                                PdfCoordinateConverter.toPixelX(t.width()),
                                PdfCoordinateConverter.toPixelX(t.height()),
                                image.getWidth(), image.getHeight(), CandidateFactSource.TEXT_LAYER))
                        .toList();

                results.add(new PageResult(pageNumber, pixelTokens, renderedPng,
                        image.getWidth(), image.getHeight(), needsOcr));
            }
        }
        return results;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=PdfLayoutParserTest`

Expected: both tests PASS. **If `topToken.get().y()` is NOT less than `bottomToken.get().y()`** (i.e. the ordering assertion fails), PDFBox's `TextPosition.getY()` is bottom-up rather than the top-down convention this implementation assumes — fix by changing `PdfCoordinateConverter.toPixelY` to flip: it will need the page height in points as a second parameter (`pageHeightPt - pointValue`) computed from `document.getPage(i).getMediaBox().getHeight()` in `PdfLayoutParser`, passed through to the conversion call. Re-run until this test passes; this is the empirical check the spec calls out as the actual arbiter of that coordinate-space question.

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/java/com/syndicate/ingestion/TokenCollectingStripper.java \
        backend/src/main/java/com/syndicate/ingestion/PdfLayoutParser.java \
        backend/src/test/java/com/syndicate/ingestion/PdfLayoutParserTest.java
git commit -m "feat(ingestion): PDFBox text-layer parsing and page rendering at 200 DPI"
```

---

### Task 4: Tesseract OCR integration

**Files:**
- Create: `backend/src/main/java/com/syndicate/ingestion/TesseractTsvParser.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/TesseractOcrRunner.java`
- Create: `backend/src/test/java/com/syndicate/ingestion/TesseractTsvParserTest.java`
- Create: `backend/src/test/java/com/syndicate/ingestion/TesseractOcrRunnerTest.java`

**Interfaces:**
- Consumes: `DocumentToken` (Task 2).
- Produces: `TesseractTsvParser.parse(String tsv, int pageNumber, int imageWidth, int imageHeight) -> List<DocumentToken>`; `new TesseractOcrRunner().run(byte[] pngBytes, int pageNumber, int imageWidth, int imageHeight) -> List<DocumentToken>` (throws `IOException`).

- [ ] **Step 1: Write the failing test for the TSV parser**

```java
package com.syndicate.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractTsvParserTest {

    private static final String HEADER =
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext";

    @Test
    void parsesWordLevelRowsAboveConfidenceThreshold() {
        String tsv = String.join("\n",
                HEADER,
                "1\t1\t0\t0\t0\t0\t0\t0\t1000\t1400\t-1\t",
                "5\t1\t1\t1\t1\t1\t100\t200\t80\t20\t92.5\tRevenue",
                "5\t1\t1\t1\t1\t2\t200\t200\t90\t20\t88.0\t42,18,000"
        );

        List<DocumentToken> tokens = TesseractTsvParser.parse(tsv, 1, 1000, 1400);

        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(0).text()).isEqualTo("Revenue");
        assertThat(tokens.get(0).x()).isEqualTo(100);
        assertThat(tokens.get(0).y()).isEqualTo(200);
        assertThat(tokens.get(1).text()).isEqualTo("42,18,000");
    }

    @Test
    void filtersOutLowConfidenceAndNonWordRows() {
        String tsv = String.join("\n",
                HEADER,
                "5\t1\t1\t1\t1\t1\t100\t200\t80\t20\t15.0\tnoise",
                "4\t1\t1\t1\t1\t0\t100\t200\t200\t20\t-1\t",
                "5\t1\t1\t1\t1\t2\t200\t200\t90\t20\t95.0\tGoodWord"
        );

        List<DocumentToken> tokens = TesseractTsvParser.parse(tsv, 1, 1000, 1400);

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).text()).isEqualTo("GoodWord");
    }

    @Test
    void returnsEmptyForHeaderOnlyInput() {
        assertThat(TesseractTsvParser.parse(HEADER, 1, 1000, 1400)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=TesseractTsvParserTest`
Expected: FAIL — `TesseractTsvParser` does not exist.

- [ ] **Step 3: Implement `TesseractTsvParser`**

```java
package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFactSource;

import java.util.ArrayList;
import java.util.List;

public final class TesseractTsvParser {

    private static final int MIN_CONFIDENCE = 30;
    private static final int WORD_LEVEL = 5;

    private TesseractTsvParser() {
    }

    public static List<DocumentToken> parse(String tsv, int pageNumber, int imageWidth, int imageHeight) {
        List<DocumentToken> tokens = new ArrayList<>();
        String[] lines = tsv.split("\n", -1);

        for (int i = 1; i < lines.length; i++) { // skip header row
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length < 12) {
                continue;
            }
            int level = Integer.parseInt(cols[0].trim());
            if (level != WORD_LEVEL) {
                continue;
            }
            String text = cols[11];
            if (text.isBlank()) {
                continue;
            }
            double conf = Double.parseDouble(cols[10].trim());
            if (conf < MIN_CONFIDENCE) {
                continue;
            }
            double left = Double.parseDouble(cols[6].trim());
            double top = Double.parseDouble(cols[7].trim());
            double width = Double.parseDouble(cols[8].trim());
            double height = Double.parseDouble(cols[9].trim());
            tokens.add(new DocumentToken(text, pageNumber, left, top, width, height,
                    imageWidth, imageHeight, CandidateFactSource.OCR));
        }
        return tokens;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=TesseractTsvParserTest`
Expected: PASS

- [ ] **Step 5: Write the failing test for the real Tesseract runner**

This draws real text onto a real image with Java2D and runs the actual `tesseract` binary against it — it needs `tesseract` on `PATH` (confirmed present on this host: version 5.5.3, `eng` language pack installed).

```java
package com.syndicate.ingestion;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractOcrRunnerTest {

    private final TesseractOcrRunner runner = new TesseractOcrRunner();

    @Test
    void recognizesTextFromARealRenderedImage() throws Exception {
        byte[] png = renderTextImage("REVENUE 421800");

        List<DocumentToken> tokens = runner.run(png, 1, 800, 200);

        assertThat(tokens).isNotEmpty();
        String allText = tokens.stream().map(DocumentToken::text)
                .reduce("", (a, b) -> a + " " + b).toUpperCase();
        assertThat(allText).contains("REVENUE");
        tokens.forEach(t -> {
            assertThat(t.pageNumber()).isEqualTo(1);
            assertThat(t.pageImageWidth()).isEqualTo(800);
            assertThat(t.pageImageHeight()).isEqualTo(200);
        });
    }

    private byte[] renderTextImage(String text) throws Exception {
        BufferedImage image = new BufferedImage(800, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 200);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        g.drawString(text, 40, 110);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=TesseractOcrRunnerTest`
Expected: FAIL — `TesseractOcrRunner` does not exist.

- [ ] **Step 7: Implement `TesseractOcrRunner`**

```java
package com.syndicate.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TesseractOcrRunner {

    public List<DocumentToken> run(byte[] pngBytes, int pageNumber, int imageWidth, int imageHeight)
            throws IOException {
        Path tempImage = Files.createTempFile("syndicate-ocr-", ".png");
        try {
            Files.write(tempImage, pngBytes);
            ProcessBuilder builder = new ProcessBuilder("tesseract", tempImage.toString(), "stdout", "tsv", "-l", "eng");
            Process process = builder.start();

            String tsv = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Tesseract timed out after 60 seconds");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Tesseract failed (exit " + process.exitValue() + "): " + stderr);
            }

            return TesseractTsvParser.parse(tsv, pageNumber, imageWidth, imageHeight);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Tesseract process was interrupted", e);
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=TesseractOcrRunnerTest`
Expected: PASS. (If OCR misreads the bold sans-serif text and the assertion fails, increase the font size or padding in `renderTextImage` — Tesseract is very reliable on large, high-contrast synthetic text like this, so a failure here almost always means the test image needs to be a bit larger/clearer, not that the runner is wrong.)

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/syndicate/ingestion/TesseractTsvParser.java \
        backend/src/main/java/com/syndicate/ingestion/TesseractOcrRunner.java \
        backend/src/test/java/com/syndicate/ingestion/TesseractTsvParserTest.java \
        backend/src/test/java/com/syndicate/ingestion/TesseractOcrRunnerTest.java
git commit -m "feat(ingestion): real Tesseract OCR integration for scanned pages/images"
```

---

### Task 5: Page image cache

**Files:**
- Create: `backend/src/main/java/com/syndicate/ingestion/PageImageCache.java`
- Create: `backend/src/test/java/com/syndicate/ingestion/PageImageCacheTest.java`

**Interfaces:**
- Consumes: `com.syndicate.common.ResourceNotFoundException` (existing).
- Produces: `PageImageCache(String uploadDir)` constructor; `save(UUID evidenceId, int pageNumber, byte[] pngBytes)`; `exists(UUID evidenceId, int pageNumber) -> boolean`; `load(UUID evidenceId, int pageNumber) -> org.springframework.core.io.Resource` (throws `ResourceNotFoundException` if absent).

- [ ] **Step 1: Write the failing test**

```java
package com.syndicate.ingestion;

import com.syndicate.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageImageCacheTest {

    @Test
    void savesAndLoadsAPageImage(@TempDir Path tempDir) throws Exception {
        PageImageCache cache = new PageImageCache(tempDir.toString());
        UUID evidenceId = UUID.randomUUID();
        byte[] content = {1, 2, 3, 4};

        assertThat(cache.exists(evidenceId, 1)).isFalse();

        cache.save(evidenceId, 1, content);

        assertThat(cache.exists(evidenceId, 1)).isTrue();
        byte[] loaded = cache.load(evidenceId, 1).getInputStream().readAllBytes();
        assertThat(loaded).isEqualTo(content);
    }

    @Test
    void throwsResourceNotFoundForMissingPage(@TempDir Path tempDir) {
        PageImageCache cache = new PageImageCache(tempDir.toString());

        assertThatThrownBy(() -> cache.load(UUID.randomUUID(), 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void differentPagesOfSameEvidenceAreIndependent(@TempDir Path tempDir) throws Exception {
        PageImageCache cache = new PageImageCache(tempDir.toString());
        UUID evidenceId = UUID.randomUUID();

        cache.save(evidenceId, 1, new byte[]{1});
        cache.save(evidenceId, 2, new byte[]{2});

        assertThat(cache.load(evidenceId, 1).getInputStream().readAllBytes()).isEqualTo(new byte[]{1});
        assertThat(cache.load(evidenceId, 2).getInputStream().readAllBytes()).isEqualTo(new byte[]{2});
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=PageImageCacheTest`
Expected: FAIL — `PageImageCache` does not exist.

- [ ] **Step 3: Implement `PageImageCache`**

```java
package com.syndicate.ingestion;

import com.syndicate.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class PageImageCache {

    private final Path root;

    public PageImageCache(@Value("${syndicate.storage.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + root, e);
        }
    }

    public void save(UUID evidenceId, int pageNumber, byte[] pngBytes) {
        Path target = root.resolve(fileName(evidenceId, pageNumber));
        try {
            Files.write(target, pngBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to cache page image", e);
        }
    }

    public boolean exists(UUID evidenceId, int pageNumber) {
        return Files.exists(root.resolve(fileName(evidenceId, pageNumber)));
    }

    public Resource load(UUID evidenceId, int pageNumber) {
        Path file = root.resolve(fileName(evidenceId, pageNumber));
        if (!Files.exists(file)) {
            throw new ResourceNotFoundException("No cached image for evidence " + evidenceId + " page " + pageNumber);
        }
        try {
            return new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid cached image path", e);
        }
    }

    private String fileName(UUID evidenceId, int pageNumber) {
        return evidenceId + "-page-" + pageNumber + ".png";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=PageImageCacheTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/syndicate/ingestion/PageImageCache.java \
        backend/src/test/java/com/syndicate/ingestion/PageImageCacheTest.java
git commit -m "feat(ingestion): disk cache for rendered evidence page images"
```

---

### Task 6: CandidateFact entity, repository, and DTOs

**Files:**
- Create: `backend/src/main/java/com/syndicate/candidatefact/CandidateFact.java`
- Create: `backend/src/main/java/com/syndicate/candidatefact/CandidateFactRepository.java`
- Create: `backend/src/main/java/com/syndicate/candidatefact/dto/CandidateFactDto.java`
- Create: `backend/src/main/java/com/syndicate/candidatefact/dto/AcceptCandidateFactRequest.java`
- Create: `backend/src/main/java/com/syndicate/candidatefact/dto/RejectCandidateFactRequest.java`

**Interfaces:**
- Consumes: `CandidateFactSource`, `CandidateFactStatus` (Task 2); `Workstream`, `Evidence`, `Fact`, `User` (existing).
- Produces: `CandidateFact` entity with constructor `(Workstream, Evidence, String label, String value, String period, int pageNumber, double bboxX, double bboxY, double bboxWidth, double bboxHeight, int pageImageWidth, int pageImageHeight, CandidateFactSource source)`, methods `accept(User reviewer, String note, Fact resultingFact)` and `reject(User reviewer, String note)`; `CandidateFactRepository` with `findByWorkstreamId`, `findByWorkstreamIdAndStatus`, `deleteByEvidenceId`.

- [ ] **Step 1: Create the `CandidateFact` entity**

```java
package com.syndicate.candidatefact;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "candidate_facts")
public class CandidateFact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstream_id", nullable = false)
    private Workstream workstream;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String value;

    @Column
    private String period;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "bbox_x", nullable = false)
    private double bboxX;

    @Column(name = "bbox_y", nullable = false)
    private double bboxY;

    @Column(name = "bbox_width", nullable = false)
    private double bboxWidth;

    @Column(name = "bbox_height", nullable = false)
    private double bboxHeight;

    @Column(name = "page_image_width", nullable = false)
    private int pageImageWidth;

    @Column(name = "page_image_height", nullable = false)
    private int pageImageHeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandidateFactSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CandidateFactStatus status;

    @Column(name = "review_note")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedByUser;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_fact_id")
    private Fact resultingFact;

    protected CandidateFact() {
    }

    public CandidateFact(Workstream workstream, Evidence evidence, String label, String value, String period,
                          int pageNumber, double bboxX, double bboxY, double bboxWidth, double bboxHeight,
                          int pageImageWidth, int pageImageHeight, CandidateFactSource source) {
        this.workstream = workstream;
        this.evidence = evidence;
        this.label = label;
        this.value = value;
        this.period = period;
        this.pageNumber = pageNumber;
        this.bboxX = bboxX;
        this.bboxY = bboxY;
        this.bboxWidth = bboxWidth;
        this.bboxHeight = bboxHeight;
        this.pageImageWidth = pageImageWidth;
        this.pageImageHeight = pageImageHeight;
        this.source = source;
        this.status = CandidateFactStatus.PENDING;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getPeriod() {
        return period;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public double getBboxX() {
        return bboxX;
    }

    public double getBboxY() {
        return bboxY;
    }

    public double getBboxWidth() {
        return bboxWidth;
    }

    public double getBboxHeight() {
        return bboxHeight;
    }

    public int getPageImageWidth() {
        return pageImageWidth;
    }

    public int getPageImageHeight() {
        return pageImageHeight;
    }

    public CandidateFactSource getSource() {
        return source;
    }

    public CandidateFactStatus getStatus() {
        return status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public User getReviewedByUser() {
        return reviewedByUser;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Fact getResultingFact() {
        return resultingFact;
    }

    public void accept(User reviewer, String note, Fact resultingFact) {
        this.status = CandidateFactStatus.ACCEPTED;
        this.reviewedByUser = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
        this.resultingFact = resultingFact;
    }

    public void reject(User reviewer, String note) {
        this.status = CandidateFactStatus.REJECTED;
        this.reviewedByUser = reviewer;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
    }
}
```

- [ ] **Step 2: Create the repository**

```java
package com.syndicate.candidatefact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CandidateFactRepository extends JpaRepository<CandidateFact, UUID> {
    List<CandidateFact> findByWorkstreamId(UUID workstreamId);

    List<CandidateFact> findByWorkstreamIdAndStatus(UUID workstreamId, CandidateFactStatus status);

    void deleteByEvidenceId(UUID evidenceId);
}
```

- [ ] **Step 3: Create the DTOs**

```java
package com.syndicate.candidatefact.dto;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.candidatefact.CandidateFactStatus;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record CandidateFactDto(
        UUID id,
        UUID workstreamId,
        UUID evidenceId,
        String label,
        String value,
        String period,
        int pageNumber,
        double bboxX,
        double bboxY,
        double bboxWidth,
        double bboxHeight,
        int pageImageWidth,
        int pageImageHeight,
        CandidateFactSource source,
        CandidateFactStatus status,
        String reviewNote,
        UserDto reviewedBy,
        Instant reviewedAt,
        UUID resultingFactId
) {
    public static CandidateFactDto from(CandidateFact c) {
        return new CandidateFactDto(
                c.getId(),
                c.getWorkstream().getId(),
                c.getEvidence().getId(),
                c.getLabel(),
                c.getValue(),
                c.getPeriod(),
                c.getPageNumber(),
                c.getBboxX(),
                c.getBboxY(),
                c.getBboxWidth(),
                c.getBboxHeight(),
                c.getPageImageWidth(),
                c.getPageImageHeight(),
                c.getSource(),
                c.getStatus(),
                c.getReviewNote(),
                c.getReviewedByUser() != null ? UserDto.from(c.getReviewedByUser()) : null,
                c.getReviewedAt(),
                c.getResultingFact() != null ? c.getResultingFact().getId() : null
        );
    }
}
```

```java
package com.syndicate.candidatefact.dto;

public record AcceptCandidateFactRequest(String label, String value, String unit, String period, String reviewNote) {
}
```

```java
package com.syndicate.candidatefact.dto;

public record RejectCandidateFactRequest(String reviewNote) {
}
```

- [ ] **Step 4: Boot the app to validate the entity against the Task 1 migration**

Run: `cd backend && mvn -q -o spring-boot:run` (with Postgres up from `./run.sh` or a prior run)
Expected: starts cleanly with no Hibernate schema-validation errors against `candidate_facts`. Stop it (`Ctrl+C`) once confirmed.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/syndicate/candidatefact/CandidateFact.java \
        backend/src/main/java/com/syndicate/candidatefact/CandidateFactRepository.java \
        backend/src/main/java/com/syndicate/candidatefact/dto/CandidateFactDto.java \
        backend/src/main/java/com/syndicate/candidatefact/dto/AcceptCandidateFactRequest.java \
        backend/src/main/java/com/syndicate/candidatefact/dto/RejectCandidateFactRequest.java
git commit -m "feat(candidatefact): CandidateFact entity, repository, and DTOs"
```

---

### Task 7: Extraction orchestrator (EvidenceExtractionService)

**Files:**
- Create: `backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionException.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionService.java`
- Create: `backend/src/test/java/com/syndicate/ingestion/EvidenceExtractionServiceTest.java`
- Modify: `backend/src/main/java/com/syndicate/ingestion/CandidateFactMatcher.java` (add `@Component`)
- Modify: `backend/src/main/java/com/syndicate/ingestion/PdfLayoutParser.java` (add `@Component`)
- Modify: `backend/src/main/java/com/syndicate/ingestion/TesseractOcrRunner.java` (add `@Component`)

**Interfaces:**
- Consumes: `EvidenceRepository`, `FileStorageService`, `Evidence`, `ProcessingStatus` (existing/Task 1); `CandidateFactRepository`, `CandidateFact` (Task 6); `PdfLayoutParser`, `TesseractOcrRunner`, `CandidateFactMatcher`, `PageImageCache`, `DocumentToken`, `MatchedCandidate` (Tasks 2-5).
- Produces: `EvidenceExtractionService.process(UUID evidenceId)` — idempotent, sets `Evidence.processingStatus`/`processingError`, persists `CandidateFact` rows, throws `EvidenceExtractionException` on failure (after recording `FAILED` status).

- [ ] **Step 1: Mark the Task 3-5 classes as Spring components**

Add `import org.springframework.stereotype.Component;` and `@Component` above the class declaration in:
- `PdfLayoutParser.java` → `@Component\npublic class PdfLayoutParser {`
- `TesseractOcrRunner.java` → `@Component\npublic class TesseractOcrRunner {`
- `CandidateFactMatcher.java` → `@Component\npublic class CandidateFactMatcher {`

- [ ] **Step 2: Create `EvidenceExtractionException`**

```java
package com.syndicate.ingestion;

import java.util.UUID;

public class EvidenceExtractionException extends RuntimeException {
    public EvidenceExtractionException(UUID evidenceId, Throwable cause) {
        super("Extraction failed for evidence " + evidenceId, cause);
    }
}
```

- [ ] **Step 3: Write the failing test**

```java
package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.evidence.FileStorageService;
import com.syndicate.evidence.ProcessingStatus;
import com.syndicate.workstream.Workstream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceExtractionServiceTest {

    @Mock EvidenceRepository evidenceRepository;
    @Mock CandidateFactRepository candidateFactRepository;
    @Mock FileStorageService fileStorageService;
    @Mock PdfLayoutParser pdfLayoutParser;
    @Mock TesseractOcrRunner tesseractOcrRunner;
    @Mock CandidateFactMatcher candidateFactMatcher;
    @Mock PageImageCache pageImageCache;
    @Mock Workstream workstream;

    EvidenceExtractionService service;
    UUID evidenceId;
    Evidence evidence;

    @BeforeEach
    void setUp() {
        service = new EvidenceExtractionService(evidenceRepository, candidateFactRepository, fileStorageService,
                pdfLayoutParser, tesseractOcrRunner, candidateFactMatcher, pageImageCache);
        evidenceId = UUID.randomUUID();
        evidence = mock(Evidence.class);
        when(evidence.getId()).thenReturn(evidenceId);
        when(evidence.getWorkstream()).thenReturn(workstream);
        when(evidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));
    }

    @Test
    void deletesExistingCandidatesBeforeReprocessing() throws Exception {
        when(evidence.getContentType()).thenReturn("text/plain");

        service.process(evidenceId);

        verify(candidateFactRepository).deleteByEvidenceId(evidenceId);
    }

    @Test
    void marksNotApplicableForUnsupportedContentType() throws Exception {
        when(evidence.getContentType()).thenReturn("application/msword");

        service.process(evidenceId);

        verify(evidence).setProcessingStatus(ProcessingStatus.NOT_APPLICABLE);
        verifyNoInteractions(pdfLayoutParser, tesseractOcrRunner);
    }

    @Test
    void processesPdfTextLayerAndPersistsCandidates() throws Exception {
        when(evidence.getContentType()).thenReturn("application/pdf");
        when(evidence.getStoragePath()).thenReturn("some-file.pdf");
        byte[] pdfBytes = {1, 2, 3};
        when(fileStorageService.readBytes("some-file.pdf")).thenReturn(pdfBytes);

        DocumentToken token = new DocumentToken("Revenue", 1, 10, 10, 5, 5, 1000, 1400, CandidateFactSource.TEXT_LAYER);
        byte[] pagePng = {9, 9};
        PdfLayoutParser.PageResult page = new PdfLayoutParser.PageResult(1, List.of(token), pagePng, 1000, 1400, false);
        when(pdfLayoutParser.parse(pdfBytes)).thenReturn(List.of(page));

        MatchedCandidate matched = new MatchedCandidate("Revenue", "100", "FY2026", 1, 10, 10, 5, 5, 1000, 1400,
                CandidateFactSource.TEXT_LAYER);
        when(candidateFactMatcher.match(List.of(token))).thenReturn(List.of(matched));

        service.process(evidenceId);

        verify(pageImageCache).save(evidenceId, 1, pagePng);
        verify(candidateFactRepository).save(any(CandidateFact.class));
        verify(evidence).setProcessingStatus(ProcessingStatus.COMPLETE);
        verifyNoInteractions(tesseractOcrRunner);
    }

    @Test
    void recordsFailureAndRethrowsWhenParsingBlowsUp() throws Exception {
        when(evidence.getContentType()).thenReturn("application/pdf");
        when(evidence.getStoragePath()).thenReturn("broken.pdf");
        when(fileStorageService.readBytes("broken.pdf")).thenReturn(new byte[]{1});
        when(pdfLayoutParser.parse(any())).thenThrow(new java.io.IOException("corrupt PDF"));

        assertThatThrownBy(() -> service.process(evidenceId))
                .isInstanceOf(EvidenceExtractionException.class);

        verify(evidence).setProcessingStatus(ProcessingStatus.FAILED);
        verify(evidence).setProcessingError(contains("corrupt PDF"));
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=EvidenceExtractionServiceTest`
Expected: FAIL — `EvidenceExtractionService` does not exist.

- [ ] **Step 5: Implement `EvidenceExtractionService`**

```java
package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.evidence.FileStorageService;
import com.syndicate.evidence.ProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExtractionService.class);

    private final EvidenceRepository evidenceRepository;
    private final CandidateFactRepository candidateFactRepository;
    private final FileStorageService fileStorageService;
    private final PdfLayoutParser pdfLayoutParser;
    private final TesseractOcrRunner tesseractOcrRunner;
    private final CandidateFactMatcher candidateFactMatcher;
    private final PageImageCache pageImageCache;

    public EvidenceExtractionService(EvidenceRepository evidenceRepository,
                                      CandidateFactRepository candidateFactRepository,
                                      FileStorageService fileStorageService,
                                      PdfLayoutParser pdfLayoutParser,
                                      TesseractOcrRunner tesseractOcrRunner,
                                      CandidateFactMatcher candidateFactMatcher,
                                      PageImageCache pageImageCache) {
        this.evidenceRepository = evidenceRepository;
        this.candidateFactRepository = candidateFactRepository;
        this.fileStorageService = fileStorageService;
        this.pdfLayoutParser = pdfLayoutParser;
        this.tesseractOcrRunner = tesseractOcrRunner;
        this.candidateFactMatcher = candidateFactMatcher;
        this.pageImageCache = pageImageCache;
    }

    @Transactional(noRollbackFor = Exception.class)
    public void process(UUID evidenceId) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found: " + evidenceId));

        // idempotency: a redelivered message may be reprocessing after a partial
        // previous attempt, so always start from a clean slate for this evidence
        candidateFactRepository.deleteByEvidenceId(evidenceId);
        evidence.setProcessingStatus(ProcessingStatus.PROCESSING);
        evidence.setProcessingError(null);

        try {
            runPipeline(evidence);
        } catch (Exception e) {
            log.warn("Evidence extraction failed for {}: {}", evidenceId, e.getMessage(), e);
            evidence.setProcessingStatus(ProcessingStatus.FAILED);
            evidence.setProcessingError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            throw new EvidenceExtractionException(evidenceId, e);
        }
    }

    private void runPipeline(Evidence evidence) throws IOException {
        byte[] content = fileStorageService.readBytes(evidence.getStoragePath());
        String contentType = evidence.getContentType();

        if ("application/pdf".equals(contentType)) {
            List<PdfLayoutParser.PageResult> pages = pdfLayoutParser.parse(content);
            Map<Integer, byte[]> pageImages = new HashMap<>();
            List<DocumentToken> allTokens = new ArrayList<>();

            for (PdfLayoutParser.PageResult page : pages) {
                pageImages.put(page.pageNumber, page.renderedPng);
                if (page.needsOcr) {
                    allTokens.addAll(tesseractOcrRunner.run(page.renderedPng, page.pageNumber,
                            page.imageWidth, page.imageHeight));
                } else {
                    allTokens.addAll(page.tokens);
                }
            }
            persistCandidates(evidence, candidateFactMatcher.match(allTokens), pageImages);

        } else if (contentType != null && contentType.startsWith("image/")) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IOException("Could not decode image content");
            }
            List<DocumentToken> tokens = tesseractOcrRunner.run(content, 1, image.getWidth(), image.getHeight());
            persistCandidates(evidence, candidateFactMatcher.match(tokens), Map.of(1, content));

        } else {
            evidence.setProcessingStatus(ProcessingStatus.NOT_APPLICABLE);
        }
    }

    private void persistCandidates(Evidence evidence, List<MatchedCandidate> matches, Map<Integer, byte[]> pageImages) {
        Set<Integer> cachedPages = new HashSet<>();
        for (MatchedCandidate m : matches) {
            if (cachedPages.add(m.pageNumber())) {
                pageImageCache.save(evidence.getId(), m.pageNumber(), pageImages.get(m.pageNumber()));
            }
            candidateFactRepository.save(toCandidateFact(evidence, m));
        }
        evidence.setProcessingStatus(ProcessingStatus.COMPLETE);
    }

    private CandidateFact toCandidateFact(Evidence evidence, MatchedCandidate m) {
        return new CandidateFact(
                evidence.getWorkstream(), evidence, m.label(), m.value(), m.period(),
                m.pageNumber(), m.bboxX(), m.bboxY(), m.bboxWidth(), m.bboxHeight(),
                m.pageImageWidth(), m.pageImageHeight(), m.source());
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=EvidenceExtractionServiceTest`
Expected: PASS (4 tests green)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionException.java \
        backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionService.java \
        backend/src/test/java/com/syndicate/ingestion/EvidenceExtractionServiceTest.java \
        backend/src/main/java/com/syndicate/ingestion/CandidateFactMatcher.java \
        backend/src/main/java/com/syndicate/ingestion/PdfLayoutParser.java \
        backend/src/main/java/com/syndicate/ingestion/TesseractOcrRunner.java
git commit -m "feat(ingestion): idempotent extraction orchestrator wiring PDF/OCR/matcher/cache"
```

---

### Task 8: RabbitMQ infrastructure (durable queue, publisher, listener)

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/syndicate/config/RabbitMqConfig.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionPublisher.java`
- Create: `backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionListener.java`
- Modify: `backend/src/main/java/com/syndicate/evidence/EvidenceService.java`
- Modify: `run.sh`

**Interfaces:**
- Consumes: `EvidenceExtractionService.process(UUID)` (Task 7).
- Produces: `EvidenceExtractionPublisher.publishExtractionJob(UUID evidenceId)`.

- [ ] **Step 1: Add AMQP and Retry dependencies**

Modify `backend/pom.xml`, inside `<dependencies>` (after the validation starter):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>
```

- [ ] **Step 2: Add RabbitMQ connection properties**

Modify `backend/src/main/resources/application.yml`, add under the existing `spring:` block (after `flyway:`, before `servlet:`):

```yaml
  rabbitmq:
    host: ${SYNDICATE_RABBITMQ_HOST:localhost}
    port: ${SYNDICATE_RABBITMQ_PORT:5672}
    username: ${SYNDICATE_RABBITMQ_USER:guest}
    password: ${SYNDICATE_RABBITMQ_PASSWORD:guest}
```

- [ ] **Step 3: Create `RabbitMqConfig`**

```java
package com.syndicate.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.retry.RetryInterceptorBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "evidence.extraction.exchange";
    public static final String QUEUE = "evidence.extraction.queue";
    public static final String ROUTING_KEY = "evidence.extraction";
    public static final String DLX = "evidence.extraction.dlx";
    public static final String DLQ = "evidence.extraction.dlq";

    @Bean
    public DirectExchange extractionExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue extractionQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding extractionBinding() {
        return BindingBuilder.bind(extractionQueue()).to(extractionExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return RetryInterceptorBuilder.stateless()
                .retryOperations(retryTemplate)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
```

(`RejectAndDontRequeueRecoverer` after 3 exhausted retries rejects the message without requeueing; because the queue declares `x-dead-letter-exchange`, RabbitMQ automatically routes that rejected message to `evidence.extraction.dlq` — visible in the management UI. This is separate from, and in addition to, RabbitMQ's own unacked-message redelivery on consumer/connection loss, which is what makes an app crash mid-job safe.)

- [ ] **Step 4: Create `EvidenceExtractionPublisher`**

```java
package com.syndicate.ingestion;

import com.syndicate.config.RabbitMqConfig;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceExtractionPublisher {

    private final RabbitTemplate rabbitTemplate;

    public EvidenceExtractionPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishExtractionJob(UUID evidenceId) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE,
                RabbitMqConfig.ROUTING_KEY,
                Map.of("evidenceId", evidenceId.toString()),
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                });
    }
}
```

- [ ] **Step 5: Create `EvidenceExtractionListener`**

```java
package com.syndicate.ingestion;

import com.syndicate.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceExtractionListener {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExtractionListener.class);

    private final EvidenceExtractionService evidenceExtractionService;

    public EvidenceExtractionListener(EvidenceExtractionService evidenceExtractionService) {
        this.evidenceExtractionService = evidenceExtractionService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void onExtractionJob(Map<String, String> payload) {
        UUID evidenceId = UUID.fromString(payload.get("evidenceId"));
        log.info("Processing evidence extraction job for {}", evidenceId);
        evidenceExtractionService.process(evidenceId);
    }
}
```

- [ ] **Step 6: Wire the publisher into `EvidenceService.upload()`**

Modify `backend/src/main/java/com/syndicate/evidence/EvidenceService.java`: add `import com.syndicate.ingestion.EvidenceExtractionPublisher;`, add a constructor parameter/field `EvidenceExtractionPublisher extractionPublisher`, and change the last two lines of `upload()`:

```java
        Evidence saved = evidenceRepository.save(evidence);
        extractionPublisher.publishExtractionJob(saved.getId());
        return EvidenceDto.from(saved);
```

- [ ] **Step 7: Add RabbitMQ to `run.sh`**

Modify `run.sh`: insert this block after the Postgres section (after `echo " ready"` for Postgres) and before `echo "== Backend =="`:

```bash
echo "== RabbitMQ =="
if docker ps --format '{{.Names}}' | grep -q '^syndicate-mq$'; then
  echo "already running"
elif docker ps -a --format '{{.Names}}' | grep -q '^syndicate-mq$'; then
  docker start syndicate-mq
else
  docker run -d --name syndicate-mq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
fi

echo -n "Waiting for RabbitMQ..."
until docker exec syndicate-mq rabbitmq-diagnostics -q ping >/dev/null 2>&1; do
  echo -n "."
  sleep 1
done
echo " ready"
```

- [ ] **Step 8: Manual verification — full pipeline through the real broker**

Start everything: `./run.sh` (from repo root). Confirm the log shows `Waiting for RabbitMQ... ready` before the backend starts.

Upload a real financial-looking PDF (any PDF with text like "Total Revenue 42,18,000 FY2026" works — create one with any tool, or reuse the test-PDF-generation code from Task 3's test as a one-off script if none is handy):

```bash
curl -s -F file=@/path/to/financials.pdf -F documentType=AUDITED_FINANCIAL_STATEMENT \
  http://localhost:8080/api/workstreams/<workstreamId>/evidence \
  -H "Authorization: Bearer $TOKEN"
```

Poll it:

```bash
curl -s http://localhost:8080/api/evidence/<evidenceId> -H "Authorization: Bearer $TOKEN"
```

Expected: `processingStatus` moves `PENDING` → `PROCESSING` → `COMPLETE` within a few seconds. Then:

```bash
curl -s http://localhost:8080/api/workstreams/<workstreamId>/candidate-facts -H "Authorization: Bearer $TOKEN"
```

Expected: at least one `CandidateFact` with `status: "PENDING"` and a real bounding box.

Open `http://localhost:15672` (guest/guest) — the management UI — confirm `evidence.extraction.queue` exists and shows message throughput, proving this is a real broker, not an in-memory structure.

- [ ] **Step 9: Manual verification — crash recovery (the actual point of this task)**

Upload another evidence file. Immediately (while it's likely still `PROCESSING` — a multi-page or OCR-needing file gives more of a window) kill the backend process hard: find its PID (`ps aux | grep spring-boot` or the PID printed by `run.sh`) and `kill -9 <pid>`. Restart the backend (`cd backend && mvn -q -o spring-boot:run`, with Postgres/RabbitMQ still running). Poll `GET /evidence/<evidenceId>` again.

Expected: the job resumes automatically (no manual reprocess call) and reaches `COMPLETE` or `FAILED` — never stuck at `PROCESSING` forever, and `GET /workstreams/<id>/candidate-facts` shows no duplicate candidates for that evidence. This is the concrete proof the durable-queue requirement from the spec is met.

- [ ] **Step 10: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml \
        backend/src/main/java/com/syndicate/config/RabbitMqConfig.java \
        backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionPublisher.java \
        backend/src/main/java/com/syndicate/ingestion/EvidenceExtractionListener.java \
        backend/src/main/java/com/syndicate/evidence/EvidenceService.java \
        run.sh
git commit -m "feat(ingestion): durable RabbitMQ queue for evidence extraction jobs"
```

---

### Task 9: Evidence reprocess + page-image endpoints

**Files:**
- Modify: `backend/src/main/java/com/syndicate/evidence/EvidenceService.java`
- Modify: `backend/src/main/java/com/syndicate/evidence/EvidenceController.java`

**Interfaces:**
- Consumes: `PageImageCache` (Task 5), `EvidenceExtractionPublisher` (Task 8).
- Produces: `EvidenceService.reprocess(UUID evidenceId, UUID callerId) -> EvidenceDto`; `EvidenceService.loadPageImage(UUID evidenceId, int pageNumber, UUID callerId) -> Resource`.

- [ ] **Step 1: Add the two methods to `EvidenceService`**

Add `import com.syndicate.ingestion.PageImageCache;`, add a `PageImageCache pageImageCache` constructor parameter/field, and add:

```java
    @Transactional
    public EvidenceDto reprocess(UUID evidenceId, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        evidence.setProcessingStatus(ProcessingStatus.PENDING);
        evidence.setProcessingError(null);
        extractionPublisher.publishExtractionJob(evidence.getId());
        return EvidenceDto.from(evidence);
    }

    public Resource loadPageImage(UUID evidenceId, int pageNumber, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        return pageImageCache.load(evidenceId, pageNumber);
    }
```

(`import com.syndicate.evidence.ProcessingStatus;` is already in this file's own package, no import needed.)

- [ ] **Step 2: Add the two endpoints to `EvidenceController`**

Add:

```java
    @PostMapping("/api/evidence/{id}/reprocess")
    public EvidenceDto reprocess(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return evidenceService.reprocess(id, currentUser.getId());
    }

    @GetMapping("/api/evidence/{id}/pages/{pageNumber}/image")
    public ResponseEntity<Resource> pageImage(@PathVariable UUID id, @PathVariable int pageNumber,
                                               @AuthenticationPrincipal User currentUser) {
        Resource resource = evidenceService.loadPageImage(id, pageNumber, currentUser.getId());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(resource);
    }
```

- [ ] **Step 3: Manual verification**

Using a `FAILED` evidence item from earlier testing (or force one by uploading something that will fail — e.g. an empty/corrupt PDF-named file):

```bash
curl -s -X POST http://localhost:8080/api/evidence/<evidenceId>/reprocess -H "Authorization: Bearer $TOKEN"
```

Expected: `processingStatus` resets to `PENDING` and, per the queue, eventually resolves again.

For an evidence item with at least one candidate fact (from Task 8's verification), find its `pageNumber` via `GET /workstreams/{id}/candidate-facts`, then:

```bash
curl -s http://localhost:8080/api/evidence/<evidenceId>/pages/<pageNumber>/image \
  -H "Authorization: Bearer $TOKEN" -o /tmp/page.png
file /tmp/page.png
```

Expected: `/tmp/page.png` is a valid PNG. Requesting a page number that never produced a candidate should return `404`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/syndicate/evidence/EvidenceService.java \
        backend/src/main/java/com/syndicate/evidence/EvidenceController.java
git commit -m "feat(evidence): reprocess endpoint and cached page-image endpoint"
```

---

### Task 10: CandidateFact service and controller (list/get/accept/reject)

**Files:**
- Create: `backend/src/main/java/com/syndicate/candidatefact/CandidateFactService.java`
- Create: `backend/src/main/java/com/syndicate/candidatefact/CandidateFactController.java`
- Create: `backend/src/test/java/com/syndicate/candidatefact/CandidateFactServiceTest.java`

**Interfaces:**
- Consumes: `CandidateFactRepository`, `CandidateFact` (Task 6); `FactRepository`, `Fact` (existing); `WorkstreamService.requireAccess` (existing).
- Produces: REST endpoints `GET /api/workstreams/{id}/candidate-facts`, `GET /api/candidate-facts/{id}`, `POST /api/candidate-facts/{id}/accept`, `POST /api/candidate-facts/{id}/reject`.

- [ ] **Step 1: Write the failing test**

```java
package com.syndicate.candidatefact;

import com.syndicate.candidatefact.dto.AcceptCandidateFactRequest;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.common.BadRequestException;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateFactServiceTest {

    @Mock CandidateFactRepository candidateFactRepository;
    @Mock FactRepository factRepository;
    @Mock WorkstreamService workstreamService;
    @Mock Workstream workstream;
    @Mock Evidence evidence;
    @Mock User caller;

    CandidateFactService service;
    UUID workstreamId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CandidateFactService(candidateFactRepository, factRepository, workstreamService);
        when(workstream.getId()).thenReturn(workstreamId);
        when(caller.getId()).thenReturn(callerId);
    }

    private CandidateFact pendingCandidate() {
        CandidateFact candidate = new CandidateFact(workstream, evidence, "Revenue", "100", "FY2026",
                1, 10, 10, 5, 5, 1000, 1400, CandidateFactSource.TEXT_LAYER);
        return candidate;
    }

    @Test
    void acceptCreatesARealFactLinkedToTheSourceEvidence() {
        CandidateFact candidate = pendingCandidate();
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(evidence.getWorkstream()).thenReturn(workstream);
        when(factRepository.save(any(Fact.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.accept(candidateId,
                new AcceptCandidateFactRequest(null, "42,18,000", null, null, "confirmed"), caller);

        assertThat(result.status()).isEqualTo(CandidateFactStatus.ACCEPTED);
        assertThat(candidate.getStatus()).isEqualTo(CandidateFactStatus.ACCEPTED);
        assertThat(candidate.getResultingFact()).isNotNull();
        assertThat(candidate.getResultingFact().getValue()).isEqualTo("42,18,000");
        assertThat(candidate.getResultingFact().getEvidence()).contains(evidence);
        assertThat(candidate.getReviewedByUser()).isEqualTo(caller);
    }

    @Test
    void acceptFallsBackToCandidateFieldsWhenNoOverrideGiven() {
        CandidateFact candidate = pendingCandidate();
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(evidence.getWorkstream()).thenReturn(workstream);
        when(factRepository.save(any(Fact.class))).thenAnswer(inv -> inv.getArgument(0));

        service.accept(candidateId, new AcceptCandidateFactRequest(null, null, null, null, null), caller);

        assertThat(candidate.getResultingFact().getLabel()).isEqualTo("Revenue");
        assertThat(candidate.getResultingFact().getValue()).isEqualTo("100");
        assertThat(candidate.getResultingFact().getPeriod()).isEqualTo("FY2026");
    }

    @Test
    void rejectMarksCandidateRejectedWithoutCreatingAFact() {
        CandidateFact candidate = pendingCandidate();
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        var result = service.reject(candidateId, new RejectCandidateFactRequest("not relevant"), caller);

        assertThat(result.status()).isEqualTo(CandidateFactStatus.REJECTED);
        verifyNoInteractions(factRepository);
    }

    @Test
    void cannotAcceptAnAlreadyReviewedCandidate() {
        CandidateFact candidate = pendingCandidate();
        candidate.reject(caller, "already handled");
        UUID candidateId = UUID.randomUUID();
        when(candidateFactRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.accept(candidateId,
                new AcceptCandidateFactRequest(null, null, null, null, null), caller))
                .isInstanceOf(BadRequestException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -o test -Dtest=CandidateFactServiceTest`
Expected: FAIL — `CandidateFactService` does not exist.

- [ ] **Step 3: Implement `CandidateFactService`**

```java
package com.syndicate.candidatefact;

import com.syndicate.candidatefact.dto.AcceptCandidateFactRequest;
import com.syndicate.candidatefact.dto.CandidateFactDto;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.user.User;
import com.syndicate.workstream.WorkstreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CandidateFactService {

    private final CandidateFactRepository candidateFactRepository;
    private final FactRepository factRepository;
    private final WorkstreamService workstreamService;

    public CandidateFactService(CandidateFactRepository candidateFactRepository, FactRepository factRepository,
                                 WorkstreamService workstreamService) {
        this.candidateFactRepository = candidateFactRepository;
        this.factRepository = factRepository;
        this.workstreamService = workstreamService;
    }

    public List<CandidateFactDto> list(UUID workstreamId, CandidateFactStatus statusFilter, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        List<CandidateFact> candidates = statusFilter != null
                ? candidateFactRepository.findByWorkstreamIdAndStatus(workstreamId, statusFilter)
                : candidateFactRepository.findByWorkstreamId(workstreamId);
        return candidates.stream().map(CandidateFactDto::from).toList();
    }

    public CandidateFactDto get(UUID id, UUID callerId) {
        CandidateFact candidate = findCandidate(id);
        workstreamService.requireAccess(candidate.getWorkstream().getId(), callerId);
        return CandidateFactDto.from(candidate);
    }

    @Transactional
    public CandidateFactDto accept(UUID id, AcceptCandidateFactRequest request, User caller) {
        CandidateFact candidate = findCandidate(id);
        workstreamService.requireAccess(candidate.getWorkstream().getId(), caller.getId());
        requirePending(candidate);

        String label = request.label() != null ? request.label() : candidate.getLabel();
        String value = request.value() != null ? request.value() : candidate.getValue();
        String period = request.period() != null ? request.period() : candidate.getPeriod();

        Fact fact = new Fact(candidate.getWorkstream(), label, value, request.unit(), period, null, 1, caller);
        fact.getEvidence().add(candidate.getEvidence());
        Fact savedFact = factRepository.save(fact);

        candidate.accept(caller, request.reviewNote(), savedFact);
        return CandidateFactDto.from(candidate);
    }

    @Transactional
    public CandidateFactDto reject(UUID id, RejectCandidateFactRequest request, User caller) {
        CandidateFact candidate = findCandidate(id);
        workstreamService.requireAccess(candidate.getWorkstream().getId(), caller.getId());
        requirePending(candidate);

        candidate.reject(caller, request.reviewNote());
        return CandidateFactDto.from(candidate);
    }

    private void requirePending(CandidateFact candidate) {
        if (candidate.getStatus() != CandidateFactStatus.PENDING) {
            throw new BadRequestException("This candidate fact has already been reviewed");
        }
    }

    private CandidateFact findCandidate(UUID id) {
        return candidateFactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate fact not found: " + id));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q -o test -Dtest=CandidateFactServiceTest`
Expected: PASS (4 tests green)

- [ ] **Step 5: Implement `CandidateFactController`**

```java
package com.syndicate.candidatefact;

import com.syndicate.candidatefact.dto.AcceptCandidateFactRequest;
import com.syndicate.candidatefact.dto.CandidateFactDto;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CandidateFactController {

    private final CandidateFactService candidateFactService;

    public CandidateFactController(CandidateFactService candidateFactService) {
        this.candidateFactService = candidateFactService;
    }

    @GetMapping("/api/workstreams/{workstreamId}/candidate-facts")
    public List<CandidateFactDto> list(@PathVariable UUID workstreamId,
                                        @RequestParam(required = false) CandidateFactStatus status,
                                        @AuthenticationPrincipal User currentUser) {
        return candidateFactService.list(workstreamId, status, currentUser.getId());
    }

    @GetMapping("/api/candidate-facts/{id}")
    public CandidateFactDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return candidateFactService.get(id, currentUser.getId());
    }

    @PostMapping("/api/candidate-facts/{id}/accept")
    public CandidateFactDto accept(@PathVariable UUID id,
                                    @RequestBody(required = false) AcceptCandidateFactRequest request,
                                    @AuthenticationPrincipal User currentUser) {
        AcceptCandidateFactRequest body = request != null
                ? request : new AcceptCandidateFactRequest(null, null, null, null, null);
        return candidateFactService.accept(id, body, currentUser);
    }

    @PostMapping("/api/candidate-facts/{id}/reject")
    public CandidateFactDto reject(@PathVariable UUID id,
                                    @RequestBody(required = false) RejectCandidateFactRequest request,
                                    @AuthenticationPrincipal User currentUser) {
        RejectCandidateFactRequest body = request != null ? request : new RejectCandidateFactRequest(null);
        return candidateFactService.reject(id, body, currentUser);
    }
}
```

- [ ] **Step 6: Manual verification against real pipeline output**

Using a `PENDING` candidate id from Task 8's verification:

```bash
curl -s -X POST http://localhost:8080/api/candidate-facts/<candidateId>/accept \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"value":"42,18,000","reviewNote":"matches audited statement"}'
```

Expected: `status: "ACCEPTED"`, `resultingFactId` populated. Then:

```bash
curl -s http://localhost:8080/api/facts/<resultingFactId> -H "Authorization: Bearer $TOKEN"
```

Expected: a real `Fact`, `status: "DRAFT"`, `evidenceIds` containing the source evidence's id.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/syndicate/candidatefact/CandidateFactService.java \
        backend/src/main/java/com/syndicate/candidatefact/CandidateFactController.java \
        backend/src/test/java/com/syndicate/candidatefact/CandidateFactServiceTest.java
git commit -m "feat(candidatefact): accept/reject workflow enforcing Zero AI Authority"
```

---

### Task 11: Frontend API layer

**Files:**
- Modify: `frontend/src/api/client.js`
- Modify: `frontend/src/api/evidence.js`
- Create: `frontend/src/api/candidateFacts.js`

**Interfaces:**
- Produces: `apiImageBlobUrl(path) -> Promise<string>` (object URL); `reprocessEvidence(id)`, `pageImageUrl(evidenceId, pageNumber)`; `listCandidateFacts(workstreamId, status?)`, `acceptCandidateFact(id, payload)`, `rejectCandidateFact(id, payload)`.

- [ ] **Step 1: Add `apiImageBlobUrl` to `client.js`**

Modify `frontend/src/api/client.js`, add at the end of the file:

```js
export async function apiImageBlobUrl(path) {
  const response = await fetch(`${BASE_URL}${path}`, { headers: authHeaders() });
  if (!response.ok) {
    throw new Error('Failed to load image');
  }
  const blob = await response.blob();
  return window.URL.createObjectURL(blob);
}
```

- [ ] **Step 2: Add reprocess and page-image helpers to `evidence.js`**

Modify `frontend/src/api/evidence.js`:

```js
import { apiDelete, apiDownload, apiGet, apiImageBlobUrl, apiPost, apiUpload } from './client';
```

Add at the end of the file:

```js
export function reprocessEvidence(id) {
  return apiPost(`/evidence/${id}/reprocess`);
}

export function pageImageUrl(evidenceId, pageNumber) {
  return apiImageBlobUrl(`/evidence/${evidenceId}/pages/${pageNumber}/image`);
}
```

- [ ] **Step 3: Create `candidateFacts.js`**

```js
import { apiGet, apiPost } from './client';

export function listCandidateFacts(workstreamId, status) {
  const query = status ? `?status=${status}` : '';
  return apiGet(`/workstreams/${workstreamId}/candidate-facts${query}`);
}

export function acceptCandidateFact(id, payload) {
  return apiPost(`/candidate-facts/${id}/accept`, payload);
}

export function rejectCandidateFact(id, payload) {
  return apiPost(`/candidate-facts/${id}/reject`, payload);
}
```

- [ ] **Step 4: Lint**

Run: `cd frontend && npx oxlint src`
Expected: no new errors (pre-existing warnings from before this feature are fine; this change introduces no new ones).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/client.js frontend/src/api/evidence.js frontend/src/api/candidateFacts.js
git commit -m "feat(frontend): API client functions for candidate facts and page images"
```

---

### Task 12: CandidateFactCard component and styles

**Files:**
- Create: `frontend/src/components/CandidateFactCard.jsx`
- Modify: `frontend/src/index.css`

**Interfaces:**
- Consumes: `evidenceApi.pageImageUrl` (Task 11).
- Produces: `<CandidateFactCard candidate={CandidateFactDto} onAccept={(id, payload) => Promise} onReject={(id, payload) => Promise} />`.

- [ ] **Step 1: Create `CandidateFactCard.jsx`**

```jsx
import { useEffect, useState } from 'react';
import * as evidenceApi from '../api/evidence';

export default function CandidateFactCard({ candidate, onAccept, onReject }) {
  const [imageUrl, setImageUrl] = useState(null);
  const [imageError, setImageError] = useState(null);
  const [label, setLabel] = useState(candidate.label);
  const [value, setValue] = useState(candidate.value);
  const [period, setPeriod] = useState(candidate.period || '');
  const [reviewNote, setReviewNote] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let objectUrl = null;
    let cancelled = false;
    evidenceApi.pageImageUrl(candidate.evidenceId, candidate.pageNumber)
      .then((url) => {
        if (cancelled) {
          window.URL.revokeObjectURL(url);
          return;
        }
        objectUrl = url;
        setImageUrl(url);
      })
      .catch((err) => setImageError(err.message));
    return () => {
      cancelled = true;
      if (objectUrl) window.URL.revokeObjectURL(objectUrl);
    };
  }, [candidate.evidenceId, candidate.pageNumber]);

  const leftPct = (candidate.bboxX / candidate.pageImageWidth) * 100;
  const topPct = (candidate.bboxY / candidate.pageImageHeight) * 100;
  const widthPct = (candidate.bboxWidth / candidate.pageImageWidth) * 100;
  const heightPct = (candidate.bboxHeight / candidate.pageImageHeight) * 100;

  async function handleAccept() {
    setSubmitting(true);
    try {
      await onAccept(candidate.id, { label, value, period: period || null, reviewNote: reviewNote || null });
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReject() {
    setSubmitting(true);
    try {
      await onReject(candidate.id, { reviewNote: reviewNote || null });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="candidate-fact-card">
      <div className="candidate-fact-image-wrap">
        {imageError && <div className="hint">Could not load source page: {imageError}</div>}
        {imageUrl && (
          <div className="candidate-fact-image-frame">
            <img src={imageUrl} alt={`Page ${candidate.pageNumber} of evidence`} />
            <div
              className="candidate-fact-bbox"
              style={{ left: `${leftPct}%`, top: `${topPct}%`, width: `${widthPct}%`, height: `${heightPct}%` }}
            />
          </div>
        )}
      </div>
      <div className="candidate-fact-fields">
        <span className="hint">
          Page {candidate.pageNumber} &middot; {candidate.source === 'OCR' ? 'OCR' : 'Text layer'}
        </span>
        <label>
          Label
          <input value={label} onChange={(e) => setLabel(e.target.value)} />
        </label>
        <label>
          Value
          <input value={value} onChange={(e) => setValue(e.target.value)} />
        </label>
        <label>
          Period
          <input value={period} onChange={(e) => setPeriod(e.target.value)} />
        </label>
        <label>
          Review note
          <input value={reviewNote} onChange={(e) => setReviewNote(e.target.value)} placeholder="optional" />
        </label>
        <div className="form-actions">
          <button disabled={submitting} onClick={handleAccept}>{submitting ? 'Saving...' : 'Accept'}</button>
          <button disabled={submitting} className="secondary" onClick={handleReject}>Reject</button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Add styles**

Modify `frontend/src/index.css`, append:

```css
.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}
.badge-pending { background: #fef3c7; color: #92400e; }
.badge-processing { background: #dbeafe; color: #1e40af; }
.badge-complete { background: #dcfce7; color: #166534; }
.badge-failed { background: #fee2e2; color: #991b1b; }
.badge-not_applicable { background: #f1f5f9; color: #475569; }

.candidate-fact-card {
  display: flex;
  gap: 1rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1rem;
  margin: 0.75rem 0;
  flex-wrap: wrap;
}
.candidate-fact-image-frame {
  position: relative;
  display: inline-block;
  max-width: 420px;
}
.candidate-fact-image-frame img {
  display: block;
  width: 100%;
  height: auto;
  border: 1px solid var(--border);
}
.candidate-fact-bbox {
  position: absolute;
  border: 2px solid #dc2626;
  background: rgba(220, 38, 38, 0.15);
  pointer-events: none;
}
.candidate-fact-fields {
  flex: 1;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
```

- [ ] **Step 3: Lint**

Run: `cd frontend && npx oxlint src`
Expected: no new errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/CandidateFactCard.jsx frontend/src/index.css
git commit -m "feat(frontend): CandidateFactCard with bounding-box overlay on the source page"
```

---

### Task 13: Wire evidence status/checksum and Candidate Facts into WorkstreamDetailPage

**Files:**
- Modify: `frontend/src/pages/WorkstreamDetailPage.jsx`

**Interfaces:**
- Consumes: `candidateFactsApi.{listCandidateFacts,acceptCandidateFact,rejectCandidateFact}` (Task 11), `evidenceApi.reprocessEvidence` (Task 11), `CandidateFactCard` (Task 12).

- [ ] **Step 1: Add imports and state**

Modify `frontend/src/pages/WorkstreamDetailPage.jsx`. Add imports (after the existing `issuesApi` import):

```jsx
import * as candidateFactsApi from '../api/candidateFacts';
import CandidateFactCard from '../components/CandidateFactCard';
```

Add state (after the existing `const [issues, setIssues] = useState([]);` line):

```jsx
  const [candidateFacts, setCandidateFacts] = useState([]);
```

- [ ] **Step 2: Load candidate facts alongside everything else**

Modify the `load()` function's `Promise.all` call to also fetch pending candidates, and set the new state:

```jsx
  async function load() {
    try {
      const [ws, factList, evidenceList, issueList, candidateList] = await Promise.all([
        workstreamsApi.getWorkstream(id),
        factsApi.listFacts(id),
        evidenceApi.listEvidence(id),
        issuesApi.listIssues(id),
        candidateFactsApi.listCandidateFacts(id, 'PENDING'),
      ]);
      setWorkstream(ws);
      setFacts(factList);
      setEvidence(evidenceList);
      setIssues(issueList);
      setCandidateFacts(candidateList);
    } catch (err) {
      setError(err.message);
    }
  }
```

- [ ] **Step 3: Add handlers for reprocess/accept/reject**

Add after `handleDeleteEvidence`:

```jsx
  async function handleReprocessEvidence(evidenceId) {
    setError(null);
    try {
      await evidenceApi.reprocessEvidence(evidenceId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleAcceptCandidate(candidateId, payload) {
    setError(null);
    try {
      await candidateFactsApi.acceptCandidateFact(candidateId, payload);
      await load();
    } catch (err) {
      setError(err.message);
      throw err;
    }
  }

  async function handleRejectCandidate(candidateId, payload) {
    setError(null);
    try {
      await candidateFactsApi.rejectCandidateFact(candidateId, payload);
      await load();
    } catch (err) {
      setError(err.message);
      throw err;
    }
  }

  function badgeClass(status) {
    return `badge badge-${status.toLowerCase()}`;
  }
```

- [ ] **Step 4: Add checksum/status/retry to the Evidence table**

Replace the Evidence table's `<thead>` and `<tbody>` block:

```jsx
      <table className="data-table">
        <thead>
          <tr><th>File</th><th>Type</th><th>Checksum</th><th>Status</th><th>Uploaded by</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {evidence.map((e) => (
            <tr key={e.id}>
              <td>{e.fileName}</td>
              <td>{humanize(e.documentType)}</td>
              <td className="hint">{e.fileSha256 ? `${e.fileSha256.slice(0, 12)}...` : '—'}</td>
              <td>
                <span className={badgeClass(e.processingStatus)}>{humanize(e.processingStatus)}</span>
                {e.processingStatus === 'FAILED' && e.processingError && (
                  <div className="hint">{e.processingError}</div>
                )}
              </td>
              <td>{e.uploadedBy.fullName}</td>
              <td>
                <button onClick={() => handleDownload(e.id)}>Download</button>
                {e.processingStatus === 'FAILED' && (
                  <button onClick={() => handleReprocessEvidence(e.id)}>Retry</button>
                )}
                <button className="secondary" onClick={() => handleDeleteEvidence(e.id)}>Delete</button>
              </td>
            </tr>
          ))}
          {evidence.length === 0 && <tr><td colSpan={6} className="hint">No evidence yet.</td></tr>}
        </tbody>
      </table>
```

- [ ] **Step 5: Add the Candidate Facts section**

Insert this new section between the closing `</table>` of the Evidence section and the `<div className="page-header">` that starts the Issues section:

```jsx
      <div className="page-header">
        <h2>Candidate Facts</h2>
        <button onClick={load}>Refresh</button>
      </div>
      {candidateFacts.length === 0 && <p className="hint">No pending candidate facts.</p>}
      {candidateFacts.map((c) => (
        <CandidateFactCard
          key={c.id}
          candidate={c}
          onAccept={handleAcceptCandidate}
          onReject={handleRejectCandidate}
        />
      ))}
```

- [ ] **Step 6: Lint**

Run: `cd frontend && npx oxlint src`
Expected: no new errors.

- [ ] **Step 7: Manual end-to-end verification in the browser**

With `./run.sh` running (Postgres + RabbitMQ + backend + frontend all up):

1. Log in, navigate to a workstream that already has evidence uploaded from earlier testing (or upload a fresh financial-statement-like PDF through the "Upload evidence" form).
2. Confirm the Evidence table shows a truncated checksum and a status badge that (after a manual "Refresh" / page reload) progresses to `Complete`.
3. Confirm a card appears under "Candidate Facts" showing the actual rendered page image with a red box drawn roughly over the matched number.
4. Edit the value field slightly and click Accept.
5. Confirm the card disappears from the pending list, and the Facts table above now shows a new fact with that value, `status: DRAFT`, and a linked evidence entry.
6. Upload a second file and click Reject on its resulting candidate; confirm it disappears with no new Fact created.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/WorkstreamDetailPage.jsx
git commit -m "feat(frontend): evidence processing status, checksum, and candidate-fact review UI"
```

---

## Self-Review

**Spec coverage:**
- SHA-256 checksum → Task 1.
- Spatial anchor tuple `(document_id, file_sha256, page_number, bounding_box)` → Task 6 (`CandidateFact` fields), Task 1 (`Evidence.fileSha256`).
- Spatial OCR/Layout Parser (tokens + bounding boxes) → Tasks 3 (PDFBox text layer) and 4 (Tesseract OCR).
- AI Extraction / deterministic matcher emitting CandidateFact → Task 2 (matcher) + Task 7 (orchestrator) + Task 6 (entity).
- Zero AI Authority (no auto-promotion) → Task 10 (`accept`/`reject` are the only paths to a `Fact`, both require an authenticated human).
- Durable task queue (not in-memory `@Async`) → Task 8, verified against a real crash in Step 9.
- Review UI with page image + bbox overlay → Tasks 11-13.
- Error handling table from the spec (corrupt PDF, missing Tesseract, unsupported type, consumer exception, app crash, missing cached image) → covered by Task 7's try/catch + rethrow design, Task 8's retry/DLQ config, and Task 9's 404 behavior for uncached pages.
- Manual testing plan items 1-7 from the spec → mapped directly to the "Manual verification" steps in Tasks 8-9 and 13.

**Placeholder scan:** No task step describes behavior without code. The one deliberately conditional step (Task 3, Step 6, the Y-axis flip) is a named, specific technical uncertainty with a concrete test-driven resolution path, not a vague "handle edge cases."

**Type consistency:** `DocumentToken`, `MatchedCandidate`, `PdfLayoutParser.PageResult`, `CandidateFactDto`, `AcceptCandidateFactRequest`/`RejectCandidateFactRequest` field names and types are used identically across every task that references them (cross-checked field-by-field while writing this plan).

**Gaps found and fixed during this review:** none outstanding — the Y-axis coordinate risk (the one real ambiguity in this feature) was already resolved with a test-driven fallback in Task 3.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-11-spatial-evidence-grounding.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
