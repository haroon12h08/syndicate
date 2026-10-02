package com.syndicate.jobs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable background work, held in Postgres.
 *
 * <p>Enqueueing joins the caller's transaction, so a job exists exactly when the change that
 * needs it commits. Claiming uses {@code FOR UPDATE SKIP LOCKED} so several workers (or several
 * instances of the application) never take the same job.
 */
@Component
public class JobQueue {

    public record Job(UUID id, JobType type, UUID targetId, int attempts, int maxAttempts) {
    }

    private static final String CLAIM_SQL = """
            UPDATE jobs SET status = 'RUNNING', claimed_at = now(), attempts = attempts + 1, updated_at = now()
            WHERE id IN (
                SELECT id FROM jobs
                WHERE status = 'PENDING' AND run_after <= now()
                ORDER BY run_after
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            RETURNING id, type, target_id, attempts, max_attempts
            """;

    private final JdbcTemplate jdbc;

    public JobQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Enqueues within the caller's transaction; a job already waiting for this target is kept. */
    public void enqueue(JobType type, UUID targetId) {
        jdbc.update("INSERT INTO jobs (type, target_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                type.name(), targetId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Job> claim(int batchSize) {
        return jdbc.query(CLAIM_SQL, JobQueue::mapJob, batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(UUID jobId) {
        jdbc.update("UPDATE jobs SET status = 'DONE', completed_at = now(), last_error = NULL, updated_at = now() "
                + "WHERE id = ?", jobId);
    }

    /**
     * Schedules a retry with exponential backoff, or gives up and leaves the job FAILED.
     *
     * @return true if the job will be retried
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(Job job, String error) {
        boolean retry = job.attempts() < job.maxAttempts();
        if (retry) {
            Instant next = Instant.now().plusSeconds((long) Math.pow(2, Math.min(job.attempts(), 6)));
            jdbc.update("UPDATE jobs SET status = 'PENDING', run_after = ?, last_error = ?, claimed_at = NULL, "
                    + "updated_at = now() WHERE id = ?", java.sql.Timestamp.from(next), truncate(error), job.id());
        } else {
            jdbc.update("UPDATE jobs SET status = 'FAILED', completed_at = now(), last_error = ?, updated_at = now() "
                    + "WHERE id = ?", truncate(error), job.id());
        }
        return retry;
    }

    /** Jobs left RUNNING by a process that died are returned to the queue. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int requeueAbandoned(int olderThanMinutes) {
        return jdbc.update("UPDATE jobs SET status = 'PENDING', claimed_at = NULL, updated_at = now() "
                + "WHERE status = 'RUNNING' AND claimed_at < now() - make_interval(mins => ?)", olderThanMinutes);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 2000 ? error.substring(0, 1997) + "..." : error;
    }

    private static Job mapJob(ResultSet rs, int row) throws SQLException {
        return new Job(rs.getObject("id", UUID.class), JobType.valueOf(rs.getString("type")),
                rs.getObject("target_id", UUID.class), rs.getInt("attempts"), rs.getInt("max_attempts"));
    }
}
