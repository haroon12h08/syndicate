package com.syndicate.drhp.render;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §35 & Build Plan Ticket E6: Structured document rendering integration test.
 * Verifies HTTP endpoints for PDF and DOCX export, audit logging, and tenant isolation.
 */
class DrhpExportIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;
    Actor banker;
    Actor auditor;
    Actor outsider;
    Map document;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        banker = tx.lead();
        auditor = tx.addMember(api, "AUDITOR");
        outsider = api.register("Other Merchant Bank", "MERCHANT_BANKER");

        // Establish readiness facts
        establish("issue.post_issue_paid_up_capital", "Post-issue paid-up capital", "100000000", null);
        establish("issuer.ebitda", "EBITDA", "64000000", "FY2026");
        establish("issuer.ebitda", "EBITDA", "51000000", "FY2025");
        establish("issuer.ebitda", "EBITDA", "43000000", "FY2024");
        establish("issuer.licence_expiry", "Factory licence expiry", "4102444800000", null);

        // Create disclosure
        api.create(banker, "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Capital Structure",
                        "bodyTemplate", "The post-issue paid-up capital is {{fact:issue.post_issue_paid_up_capital}}."));

        // Compile DRAFT
        Map compiled = api.call(banker, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/drhp/compile?mode=DRAFT_PREVIEW", null).getBody();
        assertThat(compiled.get("compiled")).as(String.valueOf(compiled)).isEqualTo(true);
        document = (Map) compiled.get("document");
    }

    private Map establish(String factKey, String label, String value, String period) {
        UUID evidenceId = api.uploadEvidence(banker, tx.workstreamId(), label + ".txt",
                (label + value + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        Map<String, Object> body = new HashMap<>(Map.of("factKey", factKey, "label", label, "value", value,
                "unit", "INR"));
        if (period != null) {
            body.put("period", period);
        }
        Map fact = api.call(banker, HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts", body).getBody();
        api.call(banker, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/evidence-links",
                Map.of("evidenceId", evidenceId));
        return api.call(auditor, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/verify", null).getBody();
    }

    @Test
    void exportPdfReturnsValidPdfStreamAndRecordsAudit() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(banker.token());

        ResponseEntity<byte[]> response = rest.exchange(
                "/api/drhp/versions/" + document.get("id") + "/export/pdf",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/pdf");
        assertThat(response.getBody()).isNotEmpty();
        assertThat(new String(response.getBody(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        // Verify audit event DRHP_EXPORTED
        List<Map> audit = rest.exchange(
                "/api/transactions/" + tx.transactionId() + "/audit",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
        ).getBody();

        assertThat(audit).anySatisfy(event -> {
            assertThat(event.get("action")).isEqualTo("DRHP_EXPORTED");
            assertThat(event.get("summary").toString()).contains("PDF");
        });
    }

    @Test
    void exportDocxReturnsValidDocxStream() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(banker.token());

        ResponseEntity<byte[]> response = rest.exchange(
                "/api/drhp/versions/" + document.get("id") + "/export/docx",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(response.getBody()).isNotEmpty();
        // PK zip signature
        assertThat(response.getBody()[0]).isEqualTo((byte) 'P');
        assertThat(response.getBody()[1]).isEqualTo((byte) 'K');
    }

    @Test
    void exportLatestPdfAndDocxByTransactionId() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(banker.token());

        ResponseEntity<byte[]> pdfRes = rest.exchange(
                "/api/transactions/" + tx.transactionId() + "/drhp/export/pdf",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        assertThat(pdfRes.getStatusCode().value()).isEqualTo(200);
        assertThat(pdfRes.getHeaders().getContentType().toString()).contains("application/pdf");

        ResponseEntity<byte[]> docxRes = rest.exchange(
                "/api/transactions/" + tx.transactionId() + "/drhp/export/docx",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        assertThat(docxRes.getStatusCode().value()).isEqualTo(200);
        assertThat(docxRes.getHeaders().getContentType().toString())
                .contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void unauthorizedOutsiderCannotExportDrhp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(outsider.token());

        ResponseEntity<Map> response = rest.exchange(
                "/api/drhp/versions/" + document.get("id") + "/export/pdf",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        assertThat(response.getStatusCode().value()).isIn(403, 404);
    }
}
