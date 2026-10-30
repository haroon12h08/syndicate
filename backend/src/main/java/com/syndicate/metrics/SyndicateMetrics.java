package com.syndicate.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Production metrics facade instrumentation for Prometheus / Actuator per Spec §54.
 */
@Component
public class SyndicateMetrics {

    private final MeterRegistry registry;

    private final Timer extractionTimer;
    private final Timer compilationTimer;
    private final Timer readinessTimer;

    private final Counter loginFailuresCounter;
    private final Counter lockoutCounter;
    private final Counter malwareRejectedCounter;
    private final Counter auditWriteFailuresCounter;
    private final Counter evidenceUploadsCounter;

    public SyndicateMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.extractionTimer = Timer.builder("syndicate.evidence.extraction.duration")
                .description("Time taken to extract text and candidate facts from evidence")
                .register(registry);

        this.compilationTimer = Timer.builder("syndicate.document.compilation.duration")
                .description("Time taken to compile DRHP offer document versions")
                .register(registry);

        this.readinessTimer = Timer.builder("syndicate.readiness.evaluation.duration")
                .description("Time taken to evaluate transaction regulatory readiness")
                .register(registry);

        this.loginFailuresCounter = Counter.builder("syndicate.auth.login.failures")
                .description("Total number of failed authentication attempts")
                .register(registry);

        this.lockoutCounter = Counter.builder("syndicate.auth.lockouts")
                .description("Total number of user accounts locked out due to brute-force protection")
                .register(registry);

        this.malwareRejectedCounter = Counter.builder("syndicate.evidence.malware.rejected")
                .description("Total number of uploaded files rejected due to malware signatures or corruption")
                .register(registry);

        this.auditWriteFailuresCounter = Counter.builder("syndicate.audit.write.failures")
                .description("Total number of audit trail persistence failures")
                .register(registry);

        this.evidenceUploadsCounter = Counter.builder("syndicate.evidence.uploads")
                .description("Total number of successfully uploaded evidence documents")
                .register(registry);
    }

    public void recordExtraction(Runnable runnable) {
        extractionTimer.record(runnable);
    }

    public <T> T recordExtraction(Supplier<T> supplier) {
        return extractionTimer.record(supplier);
    }

    public void recordCompilation(Runnable runnable) {
        compilationTimer.record(runnable);
    }

    public <T> T recordCompilation(Supplier<T> supplier) {
        return compilationTimer.record(supplier);
    }

    public void recordReadiness(Runnable runnable) {
        readinessTimer.record(runnable);
    }

    public <T> T recordReadiness(Supplier<T> supplier) {
        return readinessTimer.record(supplier);
    }

    public void incrementLoginFailures() {
        loginFailuresCounter.increment();
    }

    public void incrementLockouts() {
        lockoutCounter.increment();
    }

    public void incrementMalwareRejected() {
        malwareRejectedCounter.increment();
    }

    public void incrementAuditWriteFailures() {
        auditWriteFailuresCounter.increment();
    }

    public void incrementEvidenceUploads() {
        evidenceUploadsCounter.increment();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
