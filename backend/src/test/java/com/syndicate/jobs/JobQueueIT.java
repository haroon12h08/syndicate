package com.syndicate.jobs;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The job table replaces the message broker: durable, retried, and self-healing after a crash. */
class JobQueueIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JobQueue queue;
    @Autowired JdbcTemplate jdbc;

    @Test
    void uploadingEvidenceEnqueuesAndRunsExtraction() {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        UUID evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), "fs.txt",
                ("statements " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");

        awaitUntil(() -> "DONE".equals(jdbc.queryForObject(
                "SELECT status FROM jobs WHERE type = 'EVIDENCE_EXTRACTION' AND target_id = ?", String.class,
                evidenceId)));
        assertThat(jdbc.queryForObject("SELECT processing_status FROM evidence WHERE id = ?", String.class, evidenceId))
                .isNotIn("PENDING", "PROCESSING");
    }

    @Test
    void aFailingJobIsRetriedWithBackoffThenDeadLettered() {
        UUID target = UUID.randomUUID();
        jdbc.update("INSERT INTO jobs (type, target_id, max_attempts) VALUES ('EVIDENCE_EXTRACTION', ?, 2)", target);

        List<JobQueue.Job> first = claimOurs(target);
        assertThat(first).hasSize(1);
        assertThat(queue.fail(first.get(0), "boom")).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM jobs WHERE target_id = ?", String.class, target))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT run_after > now() FROM jobs WHERE target_id = ?", Boolean.class, target))
                .isTrue();

        jdbc.update("UPDATE jobs SET run_after = now(), attempts = 2 WHERE target_id = ?", target);
        JobQueue.Job retried = claimOurs(target).get(0);
        assertThat(queue.fail(retried, "boom again")).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM jobs WHERE target_id = ?", String.class, target))
                .isEqualTo("FAILED");
    }

    @Test
    void workAbandonedByACrashedProcessIsRequeued() {
        UUID target = UUID.randomUUID();
        jdbc.update("INSERT INTO jobs (type, target_id, status, claimed_at) "
                + "VALUES ('EVIDENCE_EXTRACTION', ?, 'RUNNING', now() - interval '30 minutes')", target);

        assertThat(queue.requeueAbandoned(10)).isPositive();
        assertThat(jdbc.queryForObject("SELECT status FROM jobs WHERE target_id = ?", String.class, target))
                .isEqualTo("PENDING");
    }

    /** The worker polls concurrently, so claim until this test's own job comes back. */
    private List<JobQueue.Job> claimOurs(UUID target) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<JobQueue.Job> claimed = queue.claim(10).stream()
                    .filter(j -> j.targetId().equals(target))
                    .toList();
            if (!claimed.isEmpty()) {
                return claimed;
            }
            sleep();
        }
        throw new AssertionError("job for " + target + " was never claimable");
    }

    private void awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep();
        }
        throw new AssertionError("condition never became true");
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
