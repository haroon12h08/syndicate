package com.syndicate.ai.artifact;

import com.syndicate.ai.artifact.dto.AiArtifactDto;
import com.syndicate.ai.artifact.dto.AiProvenanceMetricsDto;
import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactService;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.candidatefact.CandidateFactStatus;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.workstream.WorkstreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AiArtifactService {

    private final AiArtifactRepository aiArtifactRepository;
    private final CandidateFactService candidateFactService;
    private final WorkstreamService workstreamService;

    public AiArtifactService(AiArtifactRepository aiArtifactRepository,
                             CandidateFactService candidateFactService,
                             WorkstreamService workstreamService) {
        this.aiArtifactRepository = aiArtifactRepository;
        this.candidateFactService = candidateFactService;
        this.workstreamService = workstreamService;
    }

    public AiArtifactDto get(UUID id) {
        AiArtifact artifact = aiArtifactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI artifact not found: " + id));
        return AiArtifactDto.from(artifact);
    }

    public List<AiArtifactDto> listByEntity(String entityType, UUID entityId) {
        return aiArtifactRepository.findByResultingEntityTypeAndResultingEntityId(entityType, entityId)
                .stream()
                .map(AiArtifactDto::from)
                .toList();
    }

    public List<AiArtifactDto> listByEvidenceId(UUID evidenceId) {
        String jsonFilter = "[\"" + evidenceId + "\"]";
        return aiArtifactRepository.findByInputEntityId(jsonFilter)
                .stream()
                .map(AiArtifactDto::from)
                .toList();
    }

    public AiProvenanceMetricsDto computeMetrics(UUID workstreamId, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);

        List<CandidateFact> candidates = candidateFactService.findByWorkstreamId(workstreamId);

        long totalCandidates = candidates.size();
        List<CandidateFact> aiCandidates = candidates.stream()
                .filter(c -> c.getSource() == CandidateFactSource.AI)
                .toList();

        long aiCount = aiCandidates.size();
        long accepted = aiCandidates.stream()
                .filter(c -> c.getStatus() == CandidateFactStatus.ACCEPTED)
                .count();
        long rejected = aiCandidates.stream()
                .filter(c -> c.getStatus() == CandidateFactStatus.REJECTED)
                .count();
        long pending = aiCandidates.stream()
                .filter(c -> c.getStatus() == CandidateFactStatus.PENDING)
                .count();

        long totalDecided = accepted + rejected;
        double acceptanceRate = totalDecided > 0 ? (double) accepted / totalDecided : 0.0;

        BigDecimal totalCost = aiCandidates.stream()
                .map(CandidateFact::getAiArtifact)
                .filter(Objects::nonNull)
                .map(AiArtifact::getTotalCostUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double avgDurationSeconds = aiCandidates.stream()
                .filter(c -> c.getReviewedAt() != null && c.getCreatedAt() != null)
                .mapToLong(c -> Math.max(0, Duration.between(c.getCreatedAt(), c.getReviewedAt()).toSeconds()))
                .average()
                .orElse(0.0);

        return new AiProvenanceMetricsDto(
                totalCandidates,
                aiCount,
                accepted,
                rejected,
                pending,
                acceptanceRate,
                totalCost,
                avgDurationSeconds
        );
    }
}
