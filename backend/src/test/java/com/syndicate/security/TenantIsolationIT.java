package com.syndicate.security;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §52: a user outside the transaction must not reach any of its data. */
class TenantIsolationIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;
    Actor outsider;
    UUID evidenceId;
    UUID factId;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        outsider = api.register("Other Bank " + UUID.randomUUID(), "MERCHANT_BANKER");
        evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), "note.txt",
                "Revenue FY2026 42.18".getBytes(StandardCharsets.UTF_8), "OTHER");
        factId = api.create(tx.lead(), "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Revenue", "value", "42.18", "unit", "INR crore", "period", "FY2026"));
    }

    @Test
    void outsiderCannotReadAnyTransactionScopedResource() {
        UUID t = tx.transactionId();
        UUID w = tx.workstreamId();
        List<String> paths = List.of(
                "/api/transactions/" + t,
                "/api/transactions/" + t + "/memberships",
                "/api/transactions/" + t + "/workstreams",
                "/api/transactions/" + t + "/evidence",
                "/api/transactions/" + t + "/tasks",
                "/api/transactions/" + t + "/audit",
                "/api/transactions/" + t + "/disclosures",
                "/api/transactions/" + t + "/readiness",
                "/api/transactions/" + t + "/drhp",
                "/api/workstreams/" + w,
                "/api/workstreams/" + w + "/facts",
                "/api/workstreams/" + w + "/evidence",
                "/api/workstreams/" + w + "/issues",
                "/api/workstreams/" + w + "/candidate-facts",
                "/api/evidence/" + evidenceId,
                "/api/evidence/" + evidenceId + "/download",
                "/api/facts/" + factId,
                "/api/facts/" + factId + "/trace",
                "/api/facts/" + factId + "/history",
                "/api/companies/" + tx.companyId());

        for (String path : paths) {
            int status = api.raw(outsider, HttpMethod.GET, path).getStatusCode().value();
            assertThat(status).as("GET " + path).isIn(403, 404);
        }
        assertThat(api.raw(tx.lead(), HttpMethod.GET, "/api/facts/" + factId).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void outsiderCannotMutateTransactionState() {
        assertThat(api.call(outsider, HttpMethod.POST, "/api/facts/" + factId + "/verify", null)
                .getStatusCode().value()).isIn(403, 404);
        assertThat(api.call(outsider, HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "X", "value", "1")).getStatusCode().value()).isIn(403, 404);
        assertThat(api.raw(outsider, HttpMethod.DELETE, "/api/evidence/" + evidenceId)
                .getStatusCode().value()).isIn(403, 404);
        assertThat(api.raw(outsider, HttpMethod.DELETE, "/api/transactions/" + tx.transactionId())
                .getStatusCode().value()).isIn(403, 404);
    }
}
