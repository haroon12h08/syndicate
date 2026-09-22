package com.syndicate.invariants;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariant 15: audit records are not ordinary mutable business data. */
class AuditIntegrityIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    TestApi api;
    TransactionFixture tx;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "LEGAL_DUE_DILIGENCE");
        UUID factId = api.create(tx.lead(), "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Registered office pin code", "value", "400001"));
        api.call(tx.lead(), HttpMethod.POST, "/api/facts/" + factId + "/verify", null);
    }

    private Map verify() {
        return api.call(tx.lead(), HttpMethod.GET, "/api/transactions/" + tx.transactionId() + "/audit/verify", null)
                .getBody();
    }

    @Test
    void mutationsAppendAVerifiableHashChain() {
        Map result = verify();
        assertThat(result.get("valid")).isEqualTo(true);
        assertThat((Integer) result.get("verifiedEvents")).isGreaterThanOrEqualTo(2);
        Integer unsealed = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE transaction_id = ? AND (hash IS NULL OR correlation_id IS NULL)",
                Integer.class, tx.transactionId());
        assertThat(unsealed).isZero();
    }

    @Test
    void theDatabaseRejectsUpdatesAndDeletes() {
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET summary = 'x' WHERE transaction_id = ?",
                tx.transactionId())).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE transaction_id = ?",
                tx.transactionId())).hasMessageContaining("append-only");
    }

    @Test
    void tamperingIsDetectedByVerification() {
        // simulate an attacker with table-owner rights bypassing the trigger
        jdbc.execute("ALTER TABLE audit_events DISABLE TRIGGER audit_events_no_update_delete");
        try {
            jdbc.update("UPDATE audit_events SET summary = 'rewritten' WHERE chain_seq = "
                    + "(SELECT min(chain_seq) FROM audit_events WHERE transaction_id = ?)", tx.transactionId());
        } finally {
            jdbc.execute("ALTER TABLE audit_events ENABLE TRIGGER audit_events_no_update_delete");
        }
        Map result = verify();
        assertThat(result.get("valid")).isEqualTo(false);
        assertThat(result.get("firstInvalidEventId")).isNotNull();
    }

    @Test
    void auditTrailSurvivesTransactionDeletion() {
        var issuerAdmin = tx.addMember(api, "ISSUER_ADMIN");
        int code = api.raw(issuerAdmin, HttpMethod.DELETE, "/api/transactions/" + tx.transactionId())
                .getStatusCode().value();
        assertThat(code).isEqualTo(204);
        Integer remaining = jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE transaction_id = ?",
                Integer.class, tx.transactionId());
        assertThat(remaining).isPositive();
    }
}
