package com.syndicate.jobs;

import com.syndicate.evidence.EvidenceFailureRecorder;
import com.syndicate.ingestion.EvidenceExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs background work in-process. One deployable unit keeps the system simple to operate; when
 * extraction load justifies it, the same image can be run with {@code syndicate.jobs.enabled=false}
 * for the API and a worker-only instance alongside, with no code change.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobQueue queue;
    private final EvidenceExtractionService extractionService;
    private final EvidenceFailureRecorder failureRecorder;
    private final boolean enabled;
    private final int batchSize;

    public JobWorker(JobQueue queue, EvidenceExtractionService extractionService,
                     EvidenceFailureRecorder failureRecorder,
                     @Value("${syndicate.jobs.enabled:true}") boolean enabled,
                     @Value("${syndicate.jobs.batch-size:4}") int batchSize) {
        this.queue = queue;
        this.extractionService = extractionService;
        this.failureRecorder = failureRecorder;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${syndicate.jobs.poll-ms:1000}")
    public void pollOnce() {
        if (!enabled) {
            return;
        }
        for (JobQueue.Job job : queue.claim(batchSize)) {
            run(job);
        }
    }

    /** Returns work abandoned by a crashed process to the queue. */
    @Scheduled(fixedDelayString = "${syndicate.jobs.reaper-ms:60000}")
    public void requeueAbandoned() {
        if (!enabled) {
            return;
        }
        int requeued = queue.requeueAbandoned(10);
        if (requeued > 0) {
            log.warn("Requeued {} job(s) abandoned by an earlier process", requeued);
        }
    }

    private void run(JobQueue.Job job) {
        try {
            switch (job.type()) {
                case EVIDENCE_EXTRACTION -> extractionService.process(job.targetId());
            }
            queue.succeed(job.id());
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            boolean willRetry = queue.fail(job, message);
            if (willRetry) {
                log.warn("Job {} ({}) failed, will retry: {}", job.id(), job.type(), message);
            } else {
                log.error("Job {} ({}) failed permanently", job.id(), job.type(), e);
                if (job.type() == JobType.EVIDENCE_EXTRACTION) {
                    failureRecorder.markExtractionQueueFailure(job.targetId(), e);
                }
            }
        }
    }
}
