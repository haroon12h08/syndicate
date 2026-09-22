package com.syndicate.graph;

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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Invariants 9 and 10: changes propagate through persisted dependencies; stale output is never current. */
class ChangePropagationIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    TestApi api;
    TransactionFixture tx;
    Map fact;
    UUID disclosureId;
    UUID documentId;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        fact = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Promoter shareholding", "value", "51", "unit", "%")).getBody();
        disclosureId = api.create(tx.lead(), "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Shareholding",
                        "bodyTemplate", "Promoters hold {{fact:Promoter shareholding}}."));
        api.call(tx.lead(), HttpMethod.POST, "/api/disclosures/" + disclosureId + "/fact-links",
                Map.of("factId", fact.get("id")));
        // a compiled DRHP that printed this fact
        documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO drhp_documents (id, transaction_id, version, status, compiled_body, compiled_at) "
                + "VALUES (?, ?, 1, 'COMPILED', '[]', now())", documentId, tx.transactionId());
        jdbc.update("INSERT INTO drhp_fact_link (drhp_document_id, fact_id) VALUES (?, ?)",
                documentId, UUID.fromString((String) fact.get("id")));
    }

    @SuppressWarnings("unchecked")
    private List<Map> dependents(UUID transactionId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        return rest.exchange("/api/transactions/" + transactionId + "/dependents?type=FACT&lineageId="
                + fact.get("lineageId"), HttpMethod.GET, new HttpEntity<>(h), List.class).getBody();
    }

    @Test
    void graphFindsEveryPersistedDependent() {
        assertThat(dependents(tx.transactionId()))
                .extracting(n -> n.get("type") + ":" + n.get("id") + ":" + n.get("stale"))
                .containsExactlyInAnyOrder("DISCLOSURE:" + disclosureId + ":false", "DOCUMENT:" + documentId + ":false");
    }

    @Test
    void supersedingAFactMarksDependentsStaleInTheSameTransaction() {
        var res = api.call(tx.lead(), HttpMethod.PUT, "/api/facts/" + fact.get("id"),
                Map.of("label", "Promoter shareholding", "value", "49", "unit", "%", "reason", "board resolution"));
        assertThat(res.getStatusCode().value()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT status FROM disclosures WHERE id = ?", String.class, disclosureId))
                .isEqualTo("STALE_REQUIRING_REVIEW");
        assertThat(jdbc.queryForObject("SELECT status FROM drhp_documents WHERE id = ?", String.class, documentId))
                .isEqualTo("INVALIDATED");
        // the disclosure still pins v1, so its edge is stale; the invalidated document drops out
        assertThat(dependents(tx.transactionId()))
                .extracting(n -> n.get("type") + ":" + n.get("stale"))
                .containsExactly("DISCLOSURE:true");
    }

    @Test
    void traversalIsConfinedToTheTransaction() {
        TransactionFixture other = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(other.lead().token());
        var res = rest.exchange("/api/transactions/" + other.transactionId() + "/dependents?type=FACT&lineageId="
                + fact.get("lineageId"), HttpMethod.GET, new HttpEntity<>(h), List.class);
        assertThat(res.getBody()).isEmpty();
    }
}
