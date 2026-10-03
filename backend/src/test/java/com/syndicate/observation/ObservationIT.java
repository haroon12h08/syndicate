package com.syndicate.observation;

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

/** Spec §18, §68: a query from an exchange becomes transaction state, not an email thread. */
class ObservationIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    TestApi api;
    TransactionFixture tx;
    Actor banker;
    Actor diligence;
    UUID evidenceId;
    Map fact;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        banker = tx.lead();
        diligence = tx.addMember(api, "DUE_DILIGENCE_TEAM");
        evidenceId = api.uploadEvidence(banker, tx.workstreamId(), "ageing.txt",
                ("receivables ageing " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        fact = api.call(banker, HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("factKey", "issuer.inventory", "label", "Inventory", "value", "92000000",
                        "unit", "INR", "period", "FY2026")).getBody();
    }

    private Map record() {
        return api.call(banker, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/observations",
                Map.of("authority", "BSE", "reference", "SME/2026/114", "receivedDate", "2026-10-01",
                        "observation", "Explain the increase in inventory during FY2026.",
                        "responseDeadline", "2026-10-15", "factIds", List.of(fact.get("id")))).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anObservationCarriesWhatItIsAbout() {
        Map observation = record();
        assertThat(observation.get("status")).isEqualTo("OPEN");
        assertThat((List<Object>) observation.get("relatedFactIds")).containsExactly(fact.get("id"));
        assertThat(observation.get("responseDeadline")).isEqualTo("2026-10-15");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theResponsePathRunsThroughASecondReader() {
        UUID id = UUID.fromString((String) record().get("id"));

        var bare = api.call(banker, HttpMethod.PUT, "/api/observations/" + id + "/response",
                Map.of("response", "Inventory rose because of a bulk purchase."));
        assertThat(bare.getStatusCode().value()).isEqualTo(200); // the observation already links a fact

        assertThat(api.call(banker, HttpMethod.POST, "/api/observations/" + id + "/response/approve", null)
                .getBody().get("code")).isEqualTo("RESPONSE_NOT_INDEPENDENT");

        Map approved = api.call(diligence, HttpMethod.POST, "/api/observations/" + id + "/response/approve", null)
                .getBody();
        assertThat(approved.get("status")).isEqualTo("RESPONSE_APPROVED");
        assertThat(approved.get("approvedBy")).isNotNull();

        Map sent = api.call(diligence, HttpMethod.POST, "/api/observations/" + id + "/response/sent", null).getBody();
        assertThat(sent.get("status")).isEqualTo("RESPONDED");
    }

    @Test
    void sendingBeforeApprovalIsRefused() {
        UUID id = UUID.fromString((String) record().get("id"));
        api.call(banker, HttpMethod.PUT, "/api/observations/" + id + "/response",
                Map.of("response", "Answered."));
        assertThat(api.call(diligence, HttpMethod.POST, "/api/observations/" + id + "/response/sent", null)
                .getBody().get("code")).isEqualTo("RESPONSE_NOT_APPROVED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anOpenObservationHoldsTheTransactionBack() {
        record();
        Map workbench = api.call(banker, HttpMethod.GET, "/api/transactions/" + tx.transactionId() + "/workbench", null)
                .getBody();
        assertThat((List<Map>) workbench.get("blockers"))
                .anySatisfy(b -> {
                    assertThat(b.get("type")).isEqualTo("REGULATORY_OBSERVATION");
                    assertThat(b.get("severity")).isEqualTo("BLOCKING");
                    assertThat(String.valueOf(b.get("reason"))).contains("BSE is waiting");
                });
    }
}
