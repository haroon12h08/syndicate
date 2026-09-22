package com.syndicate.fact;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/** Plan A8: racing corrections of one fact produce exactly one successor version. */
class ConcurrentCorrectionIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyOneOfTwoConcurrentCorrectionsWins() {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "LEGAL_DUE_DILIGENCE");
        Map v1 = api.call(tx.lead(), HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts",
                Map.of("label", "Litigation amount", "value", "8")).getBody();

        for (int attempt = 0; attempt < 5; attempt++) {
            Map current = latest(tx, v1);
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Integer>> racers = List.of("9", "10").stream()
                    .map(value -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return api.call(tx.lead(), HttpMethod.PUT, "/api/facts/" + current.get("id"),
                                Map.of("label", "Litigation amount", "value", value)).getStatusCode().value();
                    }))
                    .toList();
            start.countDown();
            List<Integer> codes = racers.stream().map(CompletableFuture::join).toList();

            assertThat(codes).as("attempt " + attempt).contains(200);
            assertThat(codes.stream().filter(c -> c == 200)).hasSize(1);
            assertThat(codes.stream().filter(c -> c != 200)).allMatch(c -> c == 400 || c == 409);
        }
        Integer duplicates = jdbc.queryForObject("SELECT count(*) FROM (SELECT version FROM facts WHERE lineage_id = ?::uuid "
                + "GROUP BY version HAVING count(*) > 1) d", Integer.class, v1.get("lineageId"));
        assertThat(duplicates).isZero();
    }

    @SuppressWarnings("unchecked")
    private Map latest(TransactionFixture tx, Map v1) {
        List<Map> history = rest.exchange("/api/facts/" + v1.get("id") + "/history", HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(bearer(tx)), List.class).getBody();
        return history.get(history.size() - 1);
    }

    private static org.springframework.http.HttpHeaders bearer(TransactionFixture tx) {
        var h = new org.springframework.http.HttpHeaders();
        h.setBearerAuth(tx.lead().token());
        return h;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
