package com.syndicate.ingestion;

import java.util.UUID;

public class EvidenceExtractionException extends RuntimeException {
    public EvidenceExtractionException(UUID evidenceId, Throwable cause) {
        super("Extraction failed for evidence " + evidenceId, cause);
    }
}
