package com.syndicate.ai.classification;

import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.evidence.EvidenceDocumentType;

public record DocumentClassificationResult(
        EvidenceDocumentType documentType,
        Double confidence,
        String reasoning,
        AiArtifact artifact
) {
}
