package com.syndicate.ai.artifact;

import com.syndicate.ai.artifact.dto.AiArtifactDto;
import com.syndicate.ai.artifact.dto.AiProvenanceMetricsDto;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiArtifactController {

    private final AiArtifactService aiArtifactService;

    public AiArtifactController(AiArtifactService aiArtifactService) {
        this.aiArtifactService = aiArtifactService;
    }

    @GetMapping("/artifacts/{id}")
    public AiArtifactDto get(@PathVariable UUID id) {
        return aiArtifactService.get(id);
    }

    @GetMapping("/artifacts")
    public List<AiArtifactDto> listByEvidence(@RequestParam(required = false) UUID evidenceId,
                                              @RequestParam(required = false) String entityType,
                                              @RequestParam(required = false) UUID entityId) {
        if (evidenceId != null) {
            return aiArtifactService.listByEvidenceId(evidenceId);
        }
        if (entityType != null && entityId != null) {
            return aiArtifactService.listByEntity(entityType, entityId);
        }
        return List.of();
    }

    @GetMapping("/metrics")
    public AiProvenanceMetricsDto metrics(@RequestParam UUID workstreamId,
                                          @AuthenticationPrincipal User currentUser) {
        return aiArtifactService.computeMetrics(workstreamId, currentUser.getId());
    }
}
