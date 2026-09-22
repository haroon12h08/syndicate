package com.syndicate.invariants;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants 1-3: machine output never becomes verified truth without independent human action. */
class CandidateBoundaryIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    TestApi api;
    TransactionFixture tx;
    Actor auditor;
    UUID evidenceId;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        auditor = tx.addMember(api, "AUDITOR");
        evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), "fs.txt",
                ("audited statements " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        awaitExtractionSettled(evidenceId);
    }

    /** The extraction worker clears an evidence's candidates when it runs, so let it finish first. */
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

    /** Stands in for the extractor: a pending candidate anchored to the evidence. */
    private UUID candidate(String label, String value) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO candidate_facts (id, workstream_id, evidence_id, label, value, period, page_number, "
                + "bbox_x, bbox_y, bbox_width, bbox_height, page_image_width, page_image_height, source, status) "
                + "VALUES (?, ?, ?, ?, ?, 'FY2026', 1, 10, 10, 50, 10, 1000, 1400, 'TEXT_LAYER', 'PENDING')",
                id, tx.workstreamId(), evidenceId, label, value);
        return id;
    }

    private Map accept(Actor actor, UUID candidateId) {
        Map<String, Object> body = new HashMap<>();
        body.put("reviewNote", "matches page 4");
        return api.call(actor, HttpMethod.POST, "/api/candidate-facts/" + candidateId + "/accept", body).getBody();
    }

    private String factIdOf(UUID candidateId) {
        return jdbc.queryForObject("SELECT resulting_fact_id::text FROM candidate_facts WHERE id = ?", String.class,
                candidateId);
    }

    @Test
    void acceptingACandidateProposesADraftFactNeverAVerifiedOne() {
        UUID candidateId = candidate("Revenue", "42.18");
        accept(auditor, candidateId);

        Map fact = api.call(auditor, HttpMethod.GET, "/api/facts/" + factIdOf(candidateId), null).getBody();
        assertThat(fact.get("status")).isEqualTo("DRAFT");
        assertThat(fact.get("origin")).isEqualTo("CANDIDATE");
        assertThat(fact.get("materiality")).isEqualTo("MATERIAL");
        assertThat(fact.get("verifiedBy")).isNull();
    }

    @Test
    void theAccepterCannotAlsoVerifyAMaterialFact() {
        UUID candidateId = candidate("Revenue", "42.18");
        accept(auditor, candidateId);
        String factId = factIdOf(candidateId);

        var self = api.call(auditor, HttpMethod.POST, "/api/facts/" + factId + "/verify", null);
        assertThat(self.getStatusCode().value()).isEqualTo(409);
        assertThat(self.getBody().get("code")).isEqualTo("FOUR_EYES_REQUIRED");

        Actor secondAuditor = tx.addMember(api, "AUDITOR");
        var independent = api.call(secondAuditor, HttpMethod.POST, "/api/facts/" + factId + "/verify", null);
        assertThat(independent.getStatusCode().value()).isEqualTo(200);
        assertThat(independent.getBody().get("status")).isEqualTo("VERIFIED");
    }

    @Test
    void immaterialFactsMayBeVerifiedByTheirAuthor() {
        UUID factId = api.create(auditor, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Registered office pin code", "value", "400001"));
        assertThat(api.call(auditor, HttpMethod.POST, "/api/facts/" + factId + "/verify", null)
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void theDatabaseRejectsSelfVerificationOfAMaterialFactEvenOutsideTheApi() {
        UUID candidateId = candidate("EBITDA", "6.4");
        accept(auditor, candidateId);
        assertThatThrownBy(() -> jdbc.update("UPDATE facts SET status = 'VERIFIED', verified_by_user_id = created_by_user_id, "
                + "verified_at = now() WHERE id = ?::uuid", factIdOf(candidateId)))
                .hasMessageContaining("chk_facts_four_eyes");
    }

    @Test
    void onlyDraftFactsCanBeVerified() {
        UUID factId = api.create(auditor, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Registered office pin code", "value", "400001"));
        api.call(auditor, HttpMethod.PUT, "/api/facts/" + factId, Map.of("label", "Registered office pin code", "value", "400002"));

        var res = api.call(auditor, HttpMethod.POST, "/api/facts/" + factId + "/verify", null);
        assertThat(res.getBody().get("code")).isEqualTo("FACT_NOT_DRAFT");
    }

    @Test
    void pendingCandidatesAreSupersededWhenTheirSourceIsRevised() {
        UUID candidateId = candidate("Revenue", "42.18");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(("restated " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "fs-restated.txt";
            }
        });
        rest.exchange("/api/evidence/" + evidenceId + "/versions", HttpMethod.POST, new HttpEntity<>(form, h), Map.class);

        assertThat(jdbc.queryForObject("SELECT status FROM candidate_facts WHERE id = ?", String.class, candidateId))
                .isEqualTo("SUPERSEDED");
        var res = api.call(auditor, HttpMethod.POST, "/api/candidate-facts/" + candidateId + "/accept",
                Map.of("reviewNote", "late"));
        assertThat(res.getStatusCode().is2xxSuccessful()).isFalse();
    }
}
