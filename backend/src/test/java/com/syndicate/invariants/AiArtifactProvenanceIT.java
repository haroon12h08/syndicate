package com.syndicate.invariants;

import com.syndicate.ai.llm.MockLlmClient;
import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase G Integration & Invariant Tests (Spec §26-28, §73 Invariant 1).
 * Verifies that AI outputs enter solely as candidates with full immutable provenance,
 * survive provider failures gracefully, cannot bypass human review, and close the loop on human review.
 */
class AiArtifactProvenanceIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockLlmClient mockLlmClient;

    TestApi api;
    TransactionFixture tx;
    Actor lead;
    Actor auditor;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        lead = tx.lead();
        auditor = tx.addMember(api, "AUDITOR");
    }

    @AfterEach
    void tearDown() {
        mockLlmClient.clearCustomHandler();
    }

    private void awaitExtractionSettled(UUID id) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            String status = jdbc.queryForObject("SELECT processing_status FROM evidence WHERE id = ?", String.class, id);
            if (!"PENDING".equals(status) && !"PROCESSING".equals(status)) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("extraction did not settle for " + id);
    }

    @Test
    void aiExtractionRecordsFullProvenanceAndEntersSolelyAsCandidates() {
        byte[] docContent = "Audited Financial Statements: Revenue Rs. 150 Cr, EBITDA Rs. 42.18 Cr".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(lead, tx.workstreamId(), "fs-2026.txt", docContent, "OTHER");
        awaitExtractionSettled(evidenceId);

        // 1. Verify extraction_runs has both DETERMINISTIC and AI runs
        List<Map<String, Object>> runs = jdbc.queryForList(
                "SELECT run_type, status, candidates_count, ai_artifact_id FROM extraction_runs WHERE evidence_id = ? ORDER BY run_type",
                evidenceId
        );
        assertThat(runs).hasSize(2);
        Map<String, Object> aiRun = runs.stream().filter(r -> "AI".equals(r.get("run_type"))).findFirst().orElseThrow();
        assertThat(aiRun.get("status")).isEqualTo("COMPLETED");
        assertThat((Number) aiRun.get("candidates_count")).isNotNull();
        UUID aiArtifactId = (UUID) aiRun.get("ai_artifact_id");
        assertThat(aiArtifactId).isNotNull();

        // 2. Verify ai_artifacts record provenance fields
        Map<String, Object> artifact = jdbc.queryForMap(
                "SELECT * FROM ai_artifacts WHERE id = ?", aiArtifactId
        );
        assertThat(artifact.get("provider")).isEqualTo("mock");
        assertThat(artifact.get("model")).isEqualTo("mock-intelligence-v1");
        assertThat((String) artifact.get("prompt_template_hash")).hasSize(64);
        assertThat(artifact.get("review_decision")).isEqualTo("PENDING");
        assertThat(artifact.get("raw_output")).isNotNull();

        // 3. Verify candidate_facts created from AI with confidence & ai_artifact_id
        List<Map<String, Object>> candidates = jdbc.queryForList(
                "SELECT id, label, value, source, status, confidence, ai_artifact_id FROM candidate_facts WHERE evidence_id = ? AND source = 'AI'",
                evidenceId
        );
        assertThat(candidates).isNotEmpty();
        Map<String, Object> firstCandidate = candidates.get(0);
        assertThat(firstCandidate.get("status")).isEqualTo("PENDING");
        assertThat(firstCandidate.get("ai_artifact_id")).isEqualTo(aiArtifactId);
        assertThat((Double) firstCandidate.get("confidence")).isGreaterThan(0.0);

        // 4. Verify Invariant 1: candidate fact proposes DRAFT fact upon human acceptance, NEVER verified
        UUID candidateId = (UUID) firstCandidate.get("id");
        ResponseEntity<Map> acceptRes = api.call(auditor, HttpMethod.POST,
                "/api/candidate-facts/" + candidateId + "/accept",
                Map.of("reviewNote", "Auditor confirmed against page 1"));
        assertThat(acceptRes.getStatusCode().is2xxSuccessful()).isTrue();

        UUID factId = UUID.fromString(jdbc.queryForObject(
                "SELECT resulting_fact_id::text FROM candidate_facts WHERE id = ?", String.class, candidateId));
        Map<String, Object> fact = jdbc.queryForMap("SELECT status, origin, verified_by_user_id FROM facts WHERE id = ?", factId);
        assertThat(fact.get("status")).isEqualTo("DRAFT");
        assertThat(fact.get("origin")).isEqualTo("CANDIDATE");
        assertThat(fact.get("verified_by_user_id")).isNull();

        // 5. Verify provenance loop is closed on ai_artifacts
        Map<String, Object> updatedArtifact = jdbc.queryForMap(
                "SELECT review_decision, reviewer_id, resulting_entity_type, resulting_entity_id, review_notes FROM ai_artifacts WHERE id = ?",
                aiArtifactId
        );
        assertThat(updatedArtifact.get("review_decision")).isEqualTo("ACCEPTED");
        assertThat(updatedArtifact.get("reviewer_id")).isNotNull();
        assertThat(updatedArtifact.get("resulting_entity_type")).isEqualTo("Fact");
        assertThat((UUID) updatedArtifact.get("resulting_entity_id")).isEqualTo(factId);
    }

    @Test
    void acceptingAiDocumentClassificationUpdatesEvidenceAndSyncsArtifact() {
        byte[] docContent = "Minutes of the Board of Directors Meeting and Resolution".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(lead, tx.workstreamId(), "board_resolution.txt", docContent, "OTHER");
        awaitExtractionSettled(evidenceId);

        String suggestedType = jdbc.queryForObject(
                "SELECT suggested_document_type FROM evidence WHERE id = ?", String.class, evidenceId);
        assertThat(suggestedType).isEqualTo("BOARD_RESOLUTION");

        UUID classArtifactId = jdbc.queryForObject(
                "SELECT classification_ai_artifact_id FROM evidence WHERE id = ?", UUID.class, evidenceId);
        assertThat(classArtifactId).isNotNull();

        // Accept suggested classification
        ResponseEntity<Map> res = api.call(auditor, HttpMethod.POST,
                "/api/evidence/" + evidenceId + "/accept-classification", null);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody().get("documentType")).isEqualTo("BOARD_RESOLUTION");

        Map<String, Object> classArtifact = jdbc.queryForMap(
                "SELECT review_decision, resulting_entity_type, resulting_entity_id FROM ai_artifacts WHERE id = ?",
                classArtifactId
        );
        assertThat(classArtifact.get("review_decision")).isEqualTo("ACCEPTED");
        assertThat(classArtifact.get("resulting_entity_type")).isEqualTo("Evidence");
        assertThat((UUID) classArtifact.get("resulting_entity_id")).isEqualTo(evidenceId);
    }

    @Test
    void negativeTestFourEyesAppliesToAiOriginatedCandidateFacts() {
        byte[] docContent = "Financials: EBITDA 42.18 Cr".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(lead, tx.workstreamId(), "fs.txt", docContent, "OTHER");
        awaitExtractionSettled(evidenceId);

        UUID candidateId = jdbc.queryForObject(
                "SELECT id FROM candidate_facts WHERE evidence_id = ? AND source = 'AI' LIMIT 1", UUID.class, evidenceId);

        // Auditor accepts the candidate -> fact becomes DRAFT
        api.call(auditor, HttpMethod.POST, "/api/candidate-facts/" + candidateId + "/accept", Map.of("reviewNote", "ok"));
        UUID factId = jdbc.queryForObject("SELECT resulting_fact_id FROM candidate_facts WHERE id = ?", UUID.class, candidateId);

        // Auditor attempts to verify their own accepted material fact -> 409 FOUR_EYES_REQUIRED
        ResponseEntity<Map> selfVerify = api.call(auditor, HttpMethod.POST, "/api/facts/" + factId + "/verify", null);
        assertThat(selfVerify.getStatusCode().value()).isEqualTo(409);
        assertThat(selfVerify.getBody().get("code")).isEqualTo("FOUR_EYES_REQUIRED");

        // Independent auditor verifies -> 200 OK
        Actor secondAuditor = tx.addMember(api, "AUDITOR");
        ResponseEntity<Map> verifyRes = api.call(secondAuditor, HttpMethod.POST, "/api/facts/" + factId + "/verify", null);
        assertThat(verifyRes.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(verifyRes.getBody().get("status")).isEqualTo("VERIFIED");
    }

    @Test
    void negativeTestDatabaseRejectsDirectVerificationWithoutIndependentHumanAction() {
        byte[] docContent = "Financials: EBITDA 42.18 Cr".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(lead, tx.workstreamId(), "fs.txt", docContent, "OTHER");
        awaitExtractionSettled(evidenceId);

        UUID candidateId = jdbc.queryForObject(
                "SELECT id FROM candidate_facts WHERE evidence_id = ? AND source = 'AI' LIMIT 1", UUID.class, evidenceId);
        api.call(auditor, HttpMethod.POST, "/api/candidate-facts/" + candidateId + "/accept", Map.of("reviewNote", "ok"));
        UUID factId = jdbc.queryForObject("SELECT resulting_fact_id FROM candidate_facts WHERE id = ?", UUID.class, candidateId);

        // Attempting direct DB update to bypass four-eyes
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE facts SET status = 'VERIFIED', verified_by_user_id = created_by_user_id, verified_at = now() WHERE id = ?",
                factId
        )).hasMessageContaining("chk_facts_four_eyes");
    }

    @Test
    void negativeTestProviderFailurePreservesDeterministicExtractionAndMarksAiRunFailed() {
        mockLlmClient.failNextCall(new RuntimeException("LLM Gateway Timeout 504"));

        byte[] docContent = "Audited Financial Statements: Total Revenue Rs. 150 Cr".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(lead, tx.workstreamId(), "fs-err.txt", docContent, "OTHER");
        awaitExtractionSettled(evidenceId);

        // Overall status is COMPLETE because deterministic pass succeeded
        String status = jdbc.queryForObject("SELECT processing_status FROM evidence WHERE id = ?", String.class, evidenceId);
        assertThat(status).isEqualTo("COMPLETE");

        // Deterministic extraction run COMPLETED
        Map<String, Object> detRun = jdbc.queryForMap(
                "SELECT status, candidates_count FROM extraction_runs WHERE evidence_id = ? AND run_type = 'DETERMINISTIC'",
                evidenceId
        );
        assertThat(detRun.get("status")).isEqualTo("COMPLETED");

        // AI extraction run FAILED with error message
        Map<String, Object> aiRun = jdbc.queryForMap(
                "SELECT status, error_message FROM extraction_runs WHERE evidence_id = ? AND run_type = 'AI'",
                evidenceId
        );
        assertThat(aiRun.get("status")).isEqualTo("FAILED");
        assertThat((String) aiRun.get("error_message")).contains("LLM Gateway Timeout 504");
    }

    @Test
    void negativeTestMalformedModelResponseHandledGracefully() {
        mockLlmClient.setCustomHandler(prompt -> new com.syndicate.ai.llm.LlmResponse(
                "I am an AI and cannot fulfill this request properly. {invalid json",
                50, 20, java.math.BigDecimal.valueOf(0.0001), 10, "mock", "mock-model", "v1"
        ));

        byte[] docContent = "Audited Financial Statements: Total Revenue Rs. 150 Cr".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(lead, tx.workstreamId(), "fs-malformed.txt", docContent, "OTHER");
        awaitExtractionSettled(evidenceId);

        String status = jdbc.queryForObject("SELECT processing_status FROM evidence WHERE id = ?", String.class, evidenceId);
        assertThat(status).isEqualTo("COMPLETE");

        Map<String, Object> aiRun = jdbc.queryForMap(
                "SELECT status FROM extraction_runs WHERE evidence_id = ? AND run_type = 'AI'", evidenceId
        );
        assertThat(aiRun.get("status")).isEqualTo("COMPLETED");
    }

    @Test
    void negativeTestTenantIsolationForAiArtifacts() {
        byte[] docContent = "Audited Financial Statements".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(lead, tx.workstreamId(), "fs-tx1.txt", docContent, "OTHER");
        awaitExtractionSettled(evidenceId);

        UUID artifactId = jdbc.queryForObject(
                "SELECT id FROM ai_artifacts WHERE input_entity_ids @> CAST(? AS jsonb) LIMIT 1",
                UUID.class, "[\"" + evidenceId + "\"]");
        assertThat(artifactId).isNotNull();

        // Create separate transaction Tx2 with separate actor
        TransactionFixture tx2 = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        Actor tx2Lead = tx2.lead();

        // User from Tx2 requests AI metrics for Tx1 workstream -> 403 Forbidden
        ResponseEntity<Map> metricsRes = api.call(tx2Lead, HttpMethod.GET,
                "/api/ai/metrics?workstreamId=" + tx.workstreamId(), null);
        assertThat(metricsRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
