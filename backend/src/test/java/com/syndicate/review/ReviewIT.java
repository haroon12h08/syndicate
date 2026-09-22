package com.syndicate.review;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §4.7, §17: reviews bind to a version; a later change reopens them. */
class ReviewIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;
    Actor diligence;
    Map fact;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        diligence = tx.addMember(api, "DUE_DILIGENCE_TEAM");
        // promoter shareholding is CRITICAL: needs due diligence and lead banker sign-off
        fact = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Promoter shareholding", "value", "51", "unit", "%")).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map> get(String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), List.class).getBody();
    }

    private List<Map> missing() {
        return get("/api/transactions/" + tx.transactionId() + "/reviews/missing");
    }

    @Test
    void requiredReviewsAreTrackedPerRole() {
        assertThat(missing()).extracting(m -> m.get("requiredRole"))
                .containsExactlyInAnyOrder("DUE_DILIGENCE_TEAM", "LEAD_BANKER");

        var res = api.call(diligence, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/reviews",
                Map.of("decision", "APPROVED_FOR_USE"));
        assertThat(res.getStatusCode().value()).isEqualTo(201);

        assertThat(missing()).extracting(m -> m.get("requiredRole")).containsExactly("LEAD_BANKER");
    }

    @Test
    void theAuthorCannotReviewTheirOwnFact() {
        var res = api.call(tx.lead(), HttpMethod.POST, "/api/facts/" + fact.get("id") + "/reviews",
                Map.of("decision", "APPROVED_FOR_USE"));
        assertThat(res.getBody().get("code")).isEqualTo("REVIEWER_NOT_INDEPENDENT");
    }

    @Test
    void aCorrectionReopensTheReviewAndTheGraphReportsItStale() {
        api.call(diligence, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/reviews",
                Map.of("decision", "APPROVED_FOR_USE"));
        Map v2 = api.call(tx.lead(), HttpMethod.PUT, "/api/facts/" + fact.get("id"),
                Map.of("label", "Promoter shareholding", "value", "49", "unit", "%", "reason", "transfer")).getBody();

        List<Map> reviews = get("/api/facts/" + v2.get("id") + "/reviews");
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).get("current")).isEqualTo(false);
        assertThat(reviews.get(0).get("targetVersionId")).isEqualTo(fact.get("id"));
        assertThat(missing()).extracting(m -> m.get("factId") + ":" + m.get("requiredRole"))
                .containsExactlyInAnyOrder(v2.get("id") + ":DUE_DILIGENCE_TEAM", v2.get("id") + ":LEAD_BANKER");

        List<Map> impacted = get("/api/transactions/" + tx.transactionId() + "/dependents?type=FACT&lineageId="
                + fact.get("lineageId"));
        assertThat(impacted).extracting(n -> n.get("type") + ":" + n.get("stale")).containsExactly("REVIEW:true");
    }

    @Test
    void rejectionNeedsAnExplanation() {
        var res = api.call(diligence, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/reviews",
                Map.of("decision", "CHANGES_REQUESTED"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }
}
