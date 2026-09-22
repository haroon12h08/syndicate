package com.syndicate.conflict;

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
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §65, end to end: succession is not conflict; contradiction is explicit and human-resolved. */
class OwnershipConflictIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    TestApi api;
    TransactionFixture tx;
    Actor diligence;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        diligence = tx.addMember(api, "DUE_DILIGENCE_TEAM");
    }

    private Map ownership(String value, String validFrom, String validTo, String document) {
        UUID evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), document + ".txt",
                (document + " " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        Map<String, Object> body = new HashMap<>(Map.of("factKey", "issuer.promoter_ownership_pct",
                "label", "Promoter shareholding", "value", value, "unit", "%", "validFrom", validFrom));
        if (validTo != null) {
            body.put("validTo", validTo);
        }
        Map fact = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts", body)
                .getBody();
        api.call(tx.lead(), HttpMethod.POST, "/api/facts/" + fact.get("id") + "/evidence-links",
                Map.of("evidenceId", evidenceId));
        return fact;
    }

    @SuppressWarnings("unchecked")
    private List<Map> conflicts() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        return rest.exchange("/api/transactions/" + tx.transactionId() + "/conflicts", HttpMethod.GET,
                new HttpEntity<>(h), List.class).getBody();
    }

    @Test
    void theFullOwnershipScenario() {
        Map a = ownership("51", "2026-04-01T00:00:00Z", "2026-09-01T00:00:00Z", "shareholding-statement-jun");
        Map c = ownership("49", "2026-09-01T00:00:00Z", null, "updated-shareholder-record");

        // 51% until 31 August then 49% from 1 September is succession, not contradiction
        assertThat(conflicts()).isEmpty();

        Map d = ownership("51", "2026-09-30T00:00:00Z", null, "statement-as-of-30-sep");
        List<Map> open = conflicts();
        assertThat(open).hasSize(1);
        Map conflict = open.get(0);
        assertThat(conflict.get("status")).isEqualTo("OPEN");
        assertThat((List<Map>) conflict.get("members")).extracting(m -> m.get("factId"))
                .containsExactlyInAnyOrder(c.get("id"), d.get("id"));
        assertThat((List<Map>) conflict.get("members"))
                .allSatisfy(m -> assertThat((List<?>) m.get("sources")).hasSize(1));
        assertThat(a.get("id")).isNotNull();

        // neither side can be verified, and the issue cannot be ticked closed
        var verify = api.call(diligence, HttpMethod.POST, "/api/facts/" + d.get("id") + "/verify", null);
        assertThat(verify.getBody().get("code")).isEqualTo("CONFLICT_OPEN");
        var closeIssue = api.call(tx.lead(), HttpMethod.PUT, "/api/issues/" + conflict.get("issueId"),
                Map.of("title", "x", "severity", "HIGH", "status", "RESOLVED"));
        assertThat(closeIssue.getStatusCode().value()).isEqualTo(409);

        // a disclosure built on the value that will lose
        UUID disclosureId = api.create(tx.lead(), "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Promoters", "bodyTemplate", "Promoters hold 51%."));
        api.call(tx.lead(), HttpMethod.POST, "/api/disclosures/" + disclosureId + "/fact-links",
                Map.of("factId", d.get("id")));

        // only an authorised role may decide
        Actor advisor = tx.addMember(api, "ADVISOR");
        Map resolution = Map.of("chosenFactId", c.get("id"), "reason", "Board resolution effective 1 Sep; the 30 Sep "
                + "statement predates the transfer being registered");
        assertThat(api.call(advisor, HttpMethod.POST, "/api/conflicts/" + conflict.get("id") + "/resolve", resolution)
                .getStatusCode().value()).isEqualTo(403);
        Map resolved = api.call(diligence, HttpMethod.POST, "/api/conflicts/" + conflict.get("id") + "/resolve",
                resolution).getBody();

        assertThat(resolved.get("status")).isEqualTo("RESOLVED");
        Map record = ((List<Map>) resolved.get("resolutions")).get(0);
        assertThat(record.get("chosenFactId")).isEqualTo(c.get("id"));
        assertThat((List<Object>) record.get("rejectedFactIds")).containsExactly(d.get("id"));
        // the losing source is kept, not erased
        assertThat(jdbc.queryForObject("SELECT status FROM facts WHERE id = ?::uuid", String.class, d.get("id")))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT status FROM disclosures WHERE id = ?", String.class, disclosureId))
                .isEqualTo("STALE_REQUIRING_REVIEW");
        assertThat(api.call(diligence, HttpMethod.POST, "/api/facts/" + c.get("id") + "/verify", null)
                .getStatusCode().value()).isEqualTo(200);

        // a later contradiction reopens the same conflict and keeps the earlier decision on record
        ownership("52", "2026-10-01T00:00:00Z", null, "october-register");
        Map reopened = conflicts().get(0);
        assertThat(reopened.get("id")).isEqualTo(conflict.get("id"));
        assertThat(reopened.get("status")).isEqualTo("OPEN");
        assertThat((List<?>) reopened.get("resolutions")).hasSize(1);
    }

    @Test
    void correctingAValueSoSourcesAgreeClearsTheConflict() {
        ownership("42.18", "2026-04-01T00:00:00Z", null, "audited-fs");
        Map b = ownership("41.80", "2026-04-01T00:00:00Z", null, "draft-fs");
        assertThat(conflicts()).hasSize(1);

        api.call(tx.lead(), HttpMethod.PUT, "/api/facts/" + b.get("id"), Map.of("label", "Promoter shareholding",
                "value", "42.18", "unit", "%", "validFrom", "2026-04-01T00:00:00Z", "reason", "typo"));

        assertThat(conflicts()).extracting(m -> m.get("status")).containsExactly("CLEARED");
    }
}
