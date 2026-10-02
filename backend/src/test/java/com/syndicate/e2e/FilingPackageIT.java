package com.syndicate.e2e;

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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §35, §36: a filing-ready package, approved by name, that Syndicate does not itself file. */
class FilingPackageIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;
    Actor banker;
    Actor issuerAdmin;
    Actor auditor;
    Map document;
    Map capitalFact;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        banker = tx.lead();
        issuerAdmin = tx.addMember(api, "ISSUER_ADMIN");
        auditor = tx.addMember(api, "AUDITOR");
        capitalFact = establish("issue.post_issue_paid_up_capital", "Post-issue paid-up capital", "180000000");
        // the configured checks also need an operating-profit record and a valid licence
        establish("issuer.ebitda", "EBITDA", "64000000", "FY2026");
        establish("issuer.ebitda", "EBITDA", "51000000", "FY2025");
        establish("issuer.ebitda", "EBITDA", "43000000", "FY2024");
        establish("issuer.licence_expiry", "Factory licence expiry", "4102444800000");
        api.create(banker, "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Capital structure",
                        "bodyTemplate", "Post-issue paid-up capital is {{fact:issue.post_issue_paid_up_capital}}."));
        Map compiled = api.call(banker, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/drhp/compile?mode=FINAL_FILING", null).getBody();
        assertThat(compiled.get("compiled")).as(String.valueOf(compiled)).isEqualTo(true);
        document = (Map) compiled.get("document");
    }

    private Map establish(String factKey, String label, String value) {
        return establish(factKey, label, value, null);
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

    private Map filingStatus() {
        return api.call(banker, HttpMethod.GET, "/api/transactions/" + tx.transactionId() + "/filing", null).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPackageNeedsEveryRequiredApprovalAndThenCarriesItsOwnProof() throws Exception {
        Map status = filingStatus();
        assertThat((List<String>) status.get("awaitingApprovalFrom"))
                .containsExactlyInAnyOrder("ISSUER_ADMIN", "LEAD_BANKER");
        assertThat(status.get("readyToAssemble")).isEqualTo(false);

        // a role that is not an approver cannot approve
        assertThat(api.call(auditor, HttpMethod.POST, "/api/drhp/versions/" + document.get("id") + "/approvals", null)
                .getBody().get("code")).isEqualTo("APPROVAL_ROLE_NOT_REQUIRED");

        api.call(banker, HttpMethod.POST, "/api/drhp/versions/" + document.get("id") + "/approvals", null);
        assertThat(api.call(banker, HttpMethod.POST,
                "/api/drhp/versions/" + document.get("id") + "/filing-package", null)
                .getBody().get("code")).isEqualTo("APPROVAL_MISSING");

        api.call(issuerAdmin, HttpMethod.POST, "/api/drhp/versions/" + document.get("id") + "/approvals", null);
        Map assembled = api.call(banker, HttpMethod.POST,
                "/api/drhp/versions/" + document.get("id") + "/filing-package", null).getBody();
        assertThat(assembled.get("readyToAssemble")).as(String.valueOf(assembled)).isEqualTo(true);
        Map pkg = (Map) assembled.get("filingPackage");
        assertThat(pkg.get("sha256")).isNotNull();
        assertThat(pkg.get("merkleRoot")).isNotNull();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(banker.token());
        byte[] zip = rest.exchange("/api/drhp/versions/" + document.get("id") + "/filing-package", HttpMethod.GET,
                new HttpEntity<>(h), byte[].class).getBody();
        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).containsExactlyInAnyOrder("document.json", "provenance-manifest.json",
                "evidence-index.csv", "approval-certificate.txt", "README.txt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aChangeAfterApprovalInvalidatesTheApprovalAndTheDocument() {
        api.call(banker, HttpMethod.POST, "/api/drhp/versions/" + document.get("id") + "/approvals", null);
        api.call(issuerAdmin, HttpMethod.POST, "/api/drhp/versions/" + document.get("id") + "/approvals", null);

        api.call(banker, HttpMethod.PUT, "/api/facts/" + capitalFact.get("id"),
                Map.of("factKey", "issue.post_issue_paid_up_capital", "label", "Post-issue paid-up capital",
                        "value", "195000000", "unit", "INR", "reason", "resized"));

        Map status = filingStatus();
        assertThat(status.get("documentStatus")).isEqualTo("INVALIDATED");
        assertThat((List<Map>) status.get("approvals")).allSatisfy(a -> {
            assertThat(a.get("current")).isEqualTo(false);
            assertThat(a.get("invalidationReason")).isNotNull();
        });
        assertThat((List<String>) status.get("blockers")).isNotEmpty();
        assertThat(api.call(banker, HttpMethod.POST,
                "/api/drhp/versions/" + document.get("id") + "/filing-package", null)
                .getBody().get("code")).isEqualTo("DOCUMENT_INVALIDATED");
    }
}
