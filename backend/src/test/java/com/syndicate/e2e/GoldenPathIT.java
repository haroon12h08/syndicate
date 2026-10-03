package com.syndicate.e2e;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §55: one transaction walked from an empty deal to a compiled, provenance-backed document
 * and out the other side of a material change. If this test cannot be made to pass through the
 * public API, the product does not work end to end.
 */
class GoldenPathIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;
    Actor banker;
    Actor auditor;
    Actor diligence;

    private Map post(Actor actor, String path, Object body) {
        var res = api.call(actor, HttpMethod.POST, path, body);
        assertThat(res.getStatusCode().is2xxSuccessful()).as("POST " + path + " -> " + res.getBody()).isTrue();
        return res.getBody();
    }

    private Map get(Actor actor, String path) {
        return api.call(actor, HttpMethod.GET, path, null).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map> getList(Actor actor, String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(actor.token());
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), List.class).getBody();
    }

    /** Records a fact, backs it with a document, and has someone else verify it. */
    private Map establish(String workstreamPath, UUID workstreamId, String factKey, String label, String value,
                          String unit, String period, String validFrom, Actor verifier) {
        UUID evidenceId = api.uploadEvidence(banker, workstreamId, label.replace(' ', '-') + ".txt",
                (label + " " + value + " " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        Map<String, Object> body = new HashMap<>(Map.of("factKey", factKey, "label", label, "value", value));
        if (unit != null) body.put("unit", unit);
        if (period != null) body.put("period", period);
        if (validFrom != null) body.put("validFrom", validFrom);
        Map fact = post(banker, workstreamPath + "/facts", body);
        post(banker, "/api/facts/" + fact.get("id") + "/evidence-links", Map.of("evidenceId", evidenceId));
        api.call(banker, HttpMethod.PUT, "/api/evidence/" + evidenceId + "/quality",
                Map.of("quality", "ACCEPTABLE"));
        Map verified = post(verifier, "/api/facts/" + fact.get("id") + "/verify", null);
        assertThat(verified.get("status")).isEqualTo("VERIFIED");
        return verified;
    }

    @Test
    void anSmeIpoFromEmptyDealToProvenanceBackedDocument() {
        api = new TestApi(rest);

        // 1-4. organisation, company, transaction, roles
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        banker = tx.lead();
        auditor = tx.addMember(api, "AUDITOR");
        diligence = tx.addMember(api, "DUE_DILIGENCE_TEAM");
        UUID financials = UUID.fromString((String) post(banker,
                "/api/transactions/" + tx.transactionId() + "/workstreams",
                Map.of("type", "FINANCIAL_DUE_DILIGENCE")).get("id"));
        UUID regulatory = UUID.fromString((String) post(banker,
                "/api/transactions/" + tx.transactionId() + "/workstreams",
                Map.of("type", "REGULATORY_DUE_DILIGENCE")).get("id"));

        // 5-8. evidence, facts, verification — everything the configured checks need
        String capitalWs = "/api/workstreams/" + tx.workstreamId();
        String finWs = "/api/workstreams/" + financials;
        String regWs = "/api/workstreams/" + regulatory;
        Map capital = establish(capitalWs, tx.workstreamId(), "issue.post_issue_paid_up_capital",
                "Post-issue paid-up capital", "180000000", "INR", null, null, diligence);
        establish(finWs, financials, "issuer.ebitda", "EBITDA", "64000000", "INR", "FY2026", null, auditor);
        establish(finWs, financials, "issuer.ebitda", "EBITDA", "51000000", "INR", "FY2025", null, auditor);
        establish(finWs, financials, "issuer.ebitda", "EBITDA", "43000000", "INR", "FY2024", null, auditor);
        establish(regWs, regulatory, "issuer.licence_expiry", "Factory licence expiry",
                "4102444800000", null, null, null, diligence);
        Map revenue = establish(finWs, financials, "issuer.revenue", "Revenue from operations",
                "421800000", "INR", "FY2026", null, auditor);

        // 9-10. a second source contradicts the revenue, and a person settles it
        Map draftRestated = post(banker, finWs + "/facts", Map.of("factKey", "issuer.revenue",
                "label", "Revenue (draft restated)", "value", "418000000", "unit", "INR", "period", "FY2026"));
        List<Map> conflicts = getList(banker, "/api/transactions/" + tx.transactionId() + "/conflicts");
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).get("status")).isEqualTo("OPEN");
        post(auditor, "/api/conflicts/" + conflicts.get(0).get("id") + "/resolve",
                Map.of("chosenFactId", revenue.get("id"), "reason", "Audited statements prevail over the draft"));
        assertThat(get(banker, "/api/facts/" + draftRestated.get("id")).get("status")).isEqualTo("REJECTED");

        // 11. the configured checks pass on the recorded facts
        Map readiness = post(banker, "/api/transactions/" + tx.transactionId() + "/readiness/evaluate", null);
        assertThat(readiness.get("rulesFailed")).isEqualTo(0);
        assertThat(readiness.get("rulesMissingEvidence")).as(String.valueOf(readiness)).isEqualTo(0);

        // 12. the standard diligence questions are answered before a filing copy is built
        tx.settleBlockingQuestions(api, api.uploadEvidence(banker, tx.workstreamId(), "diligence-file.txt",
                ("diligence " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER"));

        // a disclosure that quotes the verified facts
        UUID disclosureId = UUID.fromString((String) post(banker,
                "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Capital structure and results",
                        "bodyTemplate", "Post-issue paid-up capital is {{fact:issue.post_issue_paid_up_capital}}. "
                                + "Revenue for FY2026 was {{fact:issuer.revenue@FY2026}}.")).get("id"));

        // 13. compile
        Map compiled = post(banker, "/api/transactions/" + tx.transactionId() + "/drhp/compile?mode=FINAL_FILING", null);
        assertThat(compiled.get("compiled")).as(String.valueOf(compiled)).isEqualTo(true);
        Map document = (Map) compiled.get("document");
        assertThat(document.get("status")).isEqualTo("COMPILED");

        // 14-15. the capital figure changes; everything built on it goes stale
        Map corrected = api.call(banker, HttpMethod.PUT, "/api/facts/" + capital.get("id"),
                Map.of("factKey", "issue.post_issue_paid_up_capital", "label", "Post-issue paid-up capital",
                        "value", "195000000", "unit", "INR", "reason", "Fresh issue resized")).getBody();
        assertThat(get(banker, "/api/transactions/" + tx.transactionId() + "/drhp").get("status"))
                .isEqualTo("INVALIDATED");
        assertThat(getList(banker, "/api/transactions/" + tx.transactionId() + "/disclosures"))
                .anySatisfy(d -> assertThat(d.get("status")).isEqualTo("STALE_REQUIRING_REVIEW"));
        Map workbench = get(banker, "/api/transactions/" + tx.transactionId() + "/workbench");
        assertThat(workbench.get("readiness")).isIn("NOT_READY", "STALE_AFTER_CHANGE");

        // 16. the new version is verified and the disclosure re-read
        post(diligence, "/api/facts/" + corrected.get("id") + "/verify", null);
        api.call(banker, HttpMethod.PUT, "/api/disclosures/" + disclosureId,
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Capital structure and results",
                        "status", "READY",
                        "bodyTemplate", "Post-issue paid-up capital is {{fact:issue.post_issue_paid_up_capital}}. "
                                + "Revenue for FY2026 was {{fact:issuer.revenue@FY2026}}."));

        // 17. compile again
        Map recompiled = post(banker, "/api/transactions/" + tx.transactionId() + "/drhp/compile?mode=FINAL_FILING", null);
        assertThat(recompiled.get("compiled")).as(String.valueOf(recompiled)).isEqualTo(true);
        Map newDocument = (Map) recompiled.get("document");
        assertThat((Integer) newDocument.get("version")).isGreaterThan((Integer) document.get("version"));

        // 18. provenance identifies the exact state behind the filing
        Map provenance = get(banker, "/api/drhp/versions/" + newDocument.get("id") + "/provenance");
        assertThat(provenance.get("merkleRoot")).isNotNull();
        assertThat((List<Map>) provenance.get("leaves"))
                .anySatisfy(leaf -> assertThat(String.valueOf(leaf.get("canonical"))).contains("195000000"))
                .anySatisfy(leaf -> assertThat(String.valueOf(leaf.get("type"))).isEqualTo("EVIDENCE"));
    }
}
