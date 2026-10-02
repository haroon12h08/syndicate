package com.syndicate.graph;

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

/** Spec §6: the consequences of a change are known before the change is made. */
class ImpactPreviewIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void correctingAFactNamesEverythingItWouldReach() {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        Actor diligence = tx.addMember(api, "DUE_DILIGENCE_TEAM");

        UUID evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), "register.txt",
                ("register " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        Map fact = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("factKey", "issuer.promoter_ownership_pct", "label", "Promoter shareholding",
                        "value", "51", "unit", "%")).getBody();
        api.call(tx.lead(), HttpMethod.POST, "/api/facts/" + fact.get("id") + "/evidence-links",
                Map.of("evidenceId", evidenceId));
        api.call(diligence, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/verify", null);
        api.call(diligence, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/reviews",
                Map.of("decision", "APPROVED_FOR_USE"));
        api.create(tx.lead(), "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Promoter holding",
                        "bodyTemplate", "Promoters hold {{fact:issuer.promoter_ownership_pct}}."));

        Map impact = api.call(tx.lead(), HttpMethod.GET, "/api/facts/" + fact.get("id") + "/impact", null).getBody();

        assertThat((List<Map>) impact.get("affected"))
                .extracting(a -> a.get("type") + ": " + a.get("title"))
                .contains("DISCLOSURE: Promoter holding")
                .anySatisfy(entry -> assertThat(entry).startsWith("REVIEW: Review by"));
        assertThat((List<Map>) impact.get("affected"))
                .allSatisfy(a -> assertThat(String.valueOf(a.get("consequence"))).isNotBlank());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFactNothingDependsOnReportsNoConsequences() {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        UUID factId = api.create(tx.lead(), "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Registered office pin code", "value", "400001"));

        Map impact = api.call(tx.lead(), HttpMethod.GET, "/api/facts/" + factId + "/impact", null).getBody();

        assertThat((List<Map>) impact.get("affected")).isEmpty();
    }
}
