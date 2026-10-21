package com.syndicate.ai.extraction;

import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.candidatefact.CandidateFact;

import java.util.List;

public record ExtractedCandidateResult(
        List<CandidateFact> candidateFacts,
        AiArtifact artifact
) {
}
