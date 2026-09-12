package com.syndicate.provenance;

import com.syndicate.provenance.dto.ProvenanceManifestDto;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ProvenanceController {

    private final ProvenanceService provenanceService;

    public ProvenanceController(ProvenanceService provenanceService) {
        this.provenanceService = provenanceService;
    }

    @GetMapping("/api/drhp/versions/{id}/provenance")
    public ProvenanceManifestDto provenance(@PathVariable UUID id,
                                             @AuthenticationPrincipal User currentUser) {
        return provenanceService.get(id, currentUser.getId());
    }
}
