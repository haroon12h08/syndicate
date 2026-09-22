package com.syndicate.invariants;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
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
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Invariant 5: evidence is preserved and traceable; nothing is overwritten or destroyed. */
class EvidenceCustodyIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
    }

    private UUID upload(String text) {
        return api.uploadEvidence(tx.lead(), tx.workstreamId(), "statement.txt",
                text.getBytes(StandardCharsets.UTF_8), "OTHER");
    }

    private Map get(String path) {
        return api.call(tx.lead(), HttpMethod.GET, path, null).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map> list(String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), List.class).getBody();
    }

    @Test
    void duplicateUploadIsRejectedWithACode() {
        upload("Revenue FY2026 42.18 " + UUID.randomUUID());
        String same = "same bytes";
        upload(same);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", named(same.getBytes(StandardCharsets.UTF_8), "copy.txt"));
        form.add("documentType", "OTHER");
        var res = rest.exchange("/api/workstreams/" + tx.workstreamId() + "/evidence", HttpMethod.POST,
                new HttpEntity<>(form, h), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().get("code")).isEqualTo("DUPLICATE_EVIDENCE");
    }

    @Test
    void newVersionPreservesThePreviousOneAndItsHash() {
        UUID v1 = upload("shareholding 51% " + UUID.randomUUID());
        String v1Hash = (String) get("/api/evidence/" + v1).get("fileSha256");

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", named(("shareholding 49% " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "v2.txt"));
        form.add("reason", "updated register");
        Map v2 = rest.exchange("/api/evidence/" + v1 + "/versions", HttpMethod.POST, new HttpEntity<>(form, h), Map.class)
                .getBody();

        assertThat(v2.get("version")).isEqualTo(2);
        assertThat(v2.get("parentEvidenceId")).isEqualTo(v1.toString());
        Map old = get("/api/evidence/" + v1);
        assertThat(old.get("fileSha256")).isEqualTo(v1Hash);
        assertThat(old.get("supersededAt")).isNotNull();
        assertThat(get("/api/evidence/" + v1 + "/integrity").get("intact")).isEqualTo(true);
        assertThat(list("/api/evidence/" + v1 + "/versions")).hasSize(2);
        // lists show only the current version
        assertThat(list("/api/workstreams/" + tx.workstreamId() + "/evidence"))
                .extracting(e -> e.get("id")).containsExactly(v2.get("id"));
    }

    @Test
    void evidenceThatSupportsAFactCannotBeArchived() {
        UUID evidenceId = upload("licence " + UUID.randomUUID());
        UUID factId = api.create(tx.lead(), "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Licence expiry", "value", "2028-03-31"));
        api.call(tx.lead(), HttpMethod.POST, "/api/facts/" + factId + "/evidence-links",
                Map.of("evidenceId", evidenceId));

        var res = api.call(tx.lead(), HttpMethod.DELETE, "/api/evidence/" + evidenceId, null);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().get("code")).isEqualTo("EVIDENCE_REFERENCED");
    }

    @Test
    void archivedEvidenceIsRetainedAndReportedAsArchived() {
        UUID evidenceId = upload("draft " + UUID.randomUUID());

        var res = api.call(tx.lead(), HttpMethod.DELETE, "/api/evidence/" + evidenceId + "?reason=wrong file", null);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        Map archived = get("/api/evidence/" + evidenceId);
        assertThat(archived.get("retentionState")).isEqualTo("ARCHIVED");
        assertThat(archived.get("archiveReason")).isEqualTo("wrong file");
        assertThat(get("/api/evidence/" + evidenceId + "/integrity").get("intact")).isEqualTo(true);
        assertThat(list("/api/transactions/" + tx.transactionId() + "/evidence"))
                .extracting(e -> e.get("id")).doesNotContain(evidenceId.toString());
    }

    private static ByteArrayResource named(byte[] content, String filename) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    @Test
    void factSupportedOnlyByInsufficientEvidenceCannotBeVerified() {
        UUID evidenceId = upload("illegible scan " + UUID.randomUUID());
        UUID factId = api.create(tx.lead(), "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Revenue", "value", "42.18", "period", "FY2026"));
        api.call(tx.lead(), HttpMethod.POST, "/api/facts/" + factId + "/evidence-links", Map.of("evidenceId", evidenceId));
        var assessed = api.call(tx.lead(), HttpMethod.PUT, "/api/evidence/" + evidenceId + "/quality",
                Map.of("quality", "INSUFFICIENT", "reason", "illegible"));
        assertThat(assessed.getBody().get("quality")).isEqualTo("INSUFFICIENT");

        // lead is LEAD_BANKER; seat an auditor who may verify financial facts
        var auditor = tx.addMember(api, "AUDITOR");
        var res = api.call(auditor, HttpMethod.POST, "/api/facts/" + factId + "/verify", null);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().get("code")).isEqualTo("EVIDENCE_INSUFFICIENT");
    }

    @Test
    void notAcceptableQualityRequiresAReasonAndSupersededIsSystemOnly() {
        UUID evidenceId = upload("doc " + UUID.randomUUID());
        assertThat(api.call(tx.lead(), HttpMethod.PUT, "/api/evidence/" + evidenceId + "/quality",
                Map.of("quality", "INVALID")).getStatusCode().value()).isEqualTo(400);
        assertThat(api.call(tx.lead(), HttpMethod.PUT, "/api/evidence/" + evidenceId + "/quality",
                Map.of("quality", "SUPERSEDED", "reason", "x")).getStatusCode().value()).isEqualTo(400);
    }
}
