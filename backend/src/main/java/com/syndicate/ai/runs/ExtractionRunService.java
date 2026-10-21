package com.syndicate.ai.runs;

import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.evidence.Evidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExtractionRunService {

    private final ExtractionRunRepository extractionRunRepository;

    public ExtractionRunService(ExtractionRunRepository extractionRunRepository) {
        this.extractionRunRepository = extractionRunRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractionRun startRun(Evidence evidence, ExtractionRunType runType) {
        ExtractionRun run = new ExtractionRun(evidence, runType);
        return extractionRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRun(ExtractionRun run, int candidatesCount, AiArtifact artifact, Long durationMs) {
        run.complete(candidatesCount, artifact, durationMs);
        extractionRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRun(ExtractionRun run, String errorMessage, Long durationMs) {
        run.fail(errorMessage, durationMs);
        extractionRunRepository.save(run);
    }

    @Transactional(readOnly = true)
    public List<ExtractionRun> listRuns(UUID evidenceId) {
        return extractionRunRepository.findByEvidenceIdOrderByCreatedAtDesc(evidenceId);
    }
}
