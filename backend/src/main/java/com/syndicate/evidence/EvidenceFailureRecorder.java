package com.syndicate.evidence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records an evidence processing failure in its own fresh transaction.
 * <p>
 * This is split out from {@link EvidenceService} because it must be invoked
 * from an {@code afterCommit()} callback, i.e. after the original transaction
 * has already committed. Calling a {@code @Transactional(REQUIRES_NEW)}
 * method on the same object that triggers it would bypass Spring's proxy
 * (self-invocation) and silently run without a transaction, so the update
 * would never be flushed. Routing the call through a separate injected bean
 * ensures the proxy - and therefore the transaction - is actually applied.
 */
@Component
public class EvidenceFailureRecorder {

    private final EvidenceRepository evidenceRepository;

    public EvidenceFailureRecorder(EvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExtractionQueueFailure(UUID evidenceId, Exception cause) {
        evidenceRepository.findById(evidenceId).ifPresent(evidence -> {
            evidence.setProcessingStatus(ProcessingStatus.FAILED);
            evidence.setProcessingError("Failed to queue extraction job: " + cause.getMessage());
        });
    }
}
