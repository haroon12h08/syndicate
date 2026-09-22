package com.syndicate.fact;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
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

/** Plan A3: facts are identified by semantic key, not by display label. */
class FactSemanticKeyIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
    }

    private Map createFact(Map<String, Object> body) {
        var res = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts", body);
        assertThat(res.getStatusCode().value()).as(String.valueOf(res.getBody())).isEqualTo(201);
        return res.getBody();
    }

    private HttpEntity<Void> auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        return new HttpEntity<>(h);
    }

    @SuppressWarnings("unchecked")
    private List<Map> conflicts() {
        List<Map> issues = rest.exchange("/api/workstreams/" + tx.workstreamId() + "/issues", HttpMethod.GET,
                auth(), List.class).getBody();
        return issues.stream().filter(i -> String.valueOf(i.get("title")).startsWith("Conflicting")).toList();
    }

    @Test
    void differentLabelsForTheSameConceptShareAKeyAndConflict() {
        Map a = createFact(Map.of("label", "Turnover", "value", "42.18", "period", "FY2026"));
        Map b = createFact(Map.of("label", "Revenue from Operations", "value", "41.80", "period", "FY2026"));

        assertThat(a.get("factKey")).isEqualTo("issuer.revenue").isEqualTo(b.get("factKey"));
        assertThat(conflicts()).hasSize(1);
    }

    @Test
    void sameKeyInDifferentPeriodsIsNotAConflict() {
        createFact(Map.of("factKey", "issuer.revenue", "label", "Revenue", "value", "30", "period", "FY2025"));
        createFact(Map.of("factKey", "issuer.revenue", "label", "Revenue", "value", "42", "period", "FY2026"));

        assertThat(conflicts()).isEmpty();
    }

    @Test
    void unknownLabelsGetAStableCustomKey() {
        Map a = createFact(Map.of("label", "Order  Book", "value", "12"));
        assertThat(a.get("factKey")).isEqualTo("custom.order_book");
    }

    @Test
    void unknownExplicitKeyIsRejected() {
        var res = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("factKey", "issuer.not_a_thing", "label", "X", "value", "1"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void correctionsKeepKeyAndLineage() {
        Map v1 = createFact(Map.of("label", "Revenue", "value", "42.18", "period", "FY2026"));
        Map v2 = api.call(tx.lead(), HttpMethod.PUT, "/api/facts/" + v1.get("id"),
                Map.of("label", "Revenue (restated)", "value", "41.80", "period", "FY2026", "reason", "restated"))
                .getBody();

        assertThat(v2.get("factKey")).isEqualTo("issuer.revenue");
        assertThat(v2.get("lineageId")).isEqualTo(v1.get("lineageId"));
        assertThat(v2.get("id")).isNotEqualTo(v1.get("id"));

        var changeKey = api.call(tx.lead(), HttpMethod.PUT, "/api/facts/" + v2.get("id"),
                Map.of("factKey", "issuer.pat", "label", "PAT", "value", "1"));
        assertThat(changeKey.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void catalogIsPublishedForPickers() {
        List<?> catalog = rest.exchange("/api/fact-definitions", HttpMethod.GET, auth(), List.class).getBody();
        assertThat(catalog).hasSizeGreaterThan(20);
    }
}
