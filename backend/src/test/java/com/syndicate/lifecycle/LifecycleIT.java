package com.syndicate.lifecycle;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §37: where a transaction stands follows from what it holds, not from a field someone sets. */
class LifecycleIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;
    Actor banker;
    Actor auditor;
    Actor issuerAdmin;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        banker = tx.lead();
        auditor = tx.addMember(api, "AUDITOR");
        issuerAdmin = tx.addMember(api, "ISSUER_ADMIN");
    }

    private Map stage() {
        return api.call(banker, HttpMethod.GET, "/api/transactions/" + tx.transactionId() + "/stage", null).getBody();
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
    @SuppressWarnings("unchecked")
    void theStageFollowsTheWork() {
        assertThat(stage().get("stage")).isEqualTo("COLLECTING_DOCUMENTS");

        establish("issue.post_issue_paid_up_capital", "Post-issue paid-up capital", "180000000", null);
        establish("issuer.ebitda", "EBITDA", "64000000", "FY2026");
        establish("issuer.ebitda", "EBITDA", "51000000", "FY2025");
        establish("issuer.ebitda", "EBITDA", "43000000", "FY2024");
        establish("issuer.licence_expiry", "Factory licence expiry", "4102444800000", null);
        assertThat(stage().get("stage")).isEqualTo("DILIGENCE");

        tx.settleBlockingQuestions(api, api.uploadEvidence(banker, tx.workstreamId(), "diligence.txt",
                ("diligence " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER"));
        api.create(banker, "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Capital structure",
                        "bodyTemplate", "Capital is {{fact:issue.post_issue_paid_up_capital}}."));
        assertThat(stage().get("stage")).isEqualTo("PREPARING_THE_DOCUMENT");

        Map compiled = api.call(banker, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/drhp/compile?mode=FINAL_FILING", null).getBody();
        assertThat(compiled.get("compiled")).as(String.valueOf(compiled)).isEqualTo(true);
        UUID documentId = UUID.fromString((String) ((Map) compiled.get("document")).get("id"));
        assertThat(stage().get("stage")).isEqualTo("AWAITING_APPROVAL");

        // filing cannot be claimed before there is something to file
        assertThat(api.call(banker, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/milestones/FILED",
                Map.of("occurredOn", "2026-10-20")).getBody().get("code")).isEqualTo("PACKAGE_NOT_ASSEMBLED");

        api.call(banker, HttpMethod.POST, "/api/drhp/versions/" + documentId + "/approvals", null);
        api.call(issuerAdmin, HttpMethod.POST, "/api/drhp/versions/" + documentId + "/approvals", null);
        api.call(banker, HttpMethod.POST, "/api/drhp/versions/" + documentId + "/filing-package", null);
        Map ready = stage();
        assertThat(ready.get("stage")).isEqualTo("FILING_READY");
        assertThat((java.util.List<Object>) ready.get("recordable")).containsExactly("FILED");

        Map filed = api.call(banker, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/milestones/FILED",
                Map.of("occurredOn", "2026-10-20", "reference", "BSE/SME/2026/118")).getBody();
        assertThat(filed.get("stage")).isEqualTo("FILED");
        assertThat(String.valueOf(filed.get("summary"))).contains("BSE/SME/2026/118");

        // an observation moves it again, without anybody setting a status
        api.call(banker, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/observations",
                Map.of("authority", "BSE", "receivedDate", "2026-10-25",
                        "observation", "Explain the increase in inventory."));
        assertThat(stage().get("stage")).isEqualTo("ANSWERING_OBSERVATIONS");
    }

    @Test
    void listingCannotBeRecordedBeforeFiling() {
        assertThat(api.call(banker, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/milestones/LISTED",
                Map.of("occurredOn", "2026-11-01")).getBody().get("code")).isEqualTo("NOT_FILED_YET");
    }

    @Test
    void recordingAMilestoneIsForTheLeadRoles() {
        Actor advisor = tx.addMember(api, "ADVISOR");
        assertThat(api.call(advisor, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/milestones/FILED",
                Map.of("occurredOn", "2026-10-20")).getStatusCode().value()).isEqualTo(403);
    }
}
