package com.syndicate.workbench;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §8, §14: readiness is derived from concrete, explainable blockers. */
class WorkbenchIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private static List<String> types(Map workbench) {
        return ((List<Map>) workbench.get("blockers")).stream().map(b -> (String) b.get("type")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void readinessMovesAsBlockersAreCleared() {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        Actor diligence = tx.addMember(api, "DUE_DILIGENCE_TEAM");
        String path = "/api/transactions/" + tx.transactionId() + "/workbench";

        Map fact = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("factKey", "issuer.authorized_capital", "label", "Authorised capital", "value", "15",
                        "unit", "INR crore")).getBody();

        tx.settleBlockingQuestions(api, api.uploadEvidence(tx.lead(), tx.workstreamId(), "diligence.txt",
                ("diligence " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER"));

        Map wb = api.call(tx.lead(), HttpMethod.GET, path, null).getBody();
        assertThat(wb.get("readiness")).isEqualTo("NOT_READY");
        assertThat(types(wb)).contains("MATERIAL_FACT_WITHOUT_EVIDENCE", "MATERIAL_FACT_UNVERIFIED", "REVIEW_MISSING");
        assertThat(((Map) wb.get("coverage")).get("materialFacts")).isEqualTo(1);

        UUID evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), "moa.txt",
                ("MoA " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        api.call(tx.lead(), HttpMethod.POST, "/api/facts/" + fact.get("id") + "/evidence-links",
                Map.of("evidenceId", evidenceId));
        api.call(diligence, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/verify", null);
        api.call(diligence, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/reviews",
                Map.of("decision", "APPROVED_FOR_USE"));

        wb = api.call(tx.lead(), HttpMethod.GET, path, null).getBody();
        // the fact is settled; what remains is rule inputs and answers awaiting a second reader
        assertThat((List<Map>) wb.get("blockers")).isNotEmpty()
                .allSatisfy(b -> assertThat(b.get("severity")).isIn("CONDITIONAL", "AWAITING_REVIEW"));
        assertThat(wb.get("readiness")).isEqualTo("CONDITIONALLY_READY");

        UUID disclosureId = api.create(tx.lead(), "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Authorised capital", "bodyTemplate", "x"));
        api.call(tx.lead(), HttpMethod.POST, "/api/disclosures/" + disclosureId + "/fact-links",
                Map.of("factId", fact.get("id")));
        api.call(tx.lead(), HttpMethod.PUT, "/api/facts/" + fact.get("id"),
                Map.of("label", "Authorised capital", "value", "20", "unit", "INR crore", "reason", "increase"));

        wb = api.call(tx.lead(), HttpMethod.GET, path, null).getBody();
        assertThat(types(wb)).contains("STALE_DISCLOSURE", "MATERIAL_FACT_UNVERIFIED");
        assertThat(wb.get("readiness")).isEqualTo("NOT_READY");
    }

    @Test
    void outsidersCannotSeeTheWorkbench() {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        Actor outsider = api.register("Other " + UUID.randomUUID(), "MERCHANT_BANKER");
        assertThat(api.call(outsider, HttpMethod.GET, "/api/transactions/" + tx.transactionId() + "/workbench", null)
                .getStatusCode().value()).isEqualTo(403);
    }
}
