package com.syndicate.fact;

import com.syndicate.fact.dto.CreateFactRequest;
import com.syndicate.fact.dto.FactDto;
import com.syndicate.fact.dto.UpdateFactRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class FactController {

    private final FactService factService;

    public FactController(FactService factService) {
        this.factService = factService;
    }

    @GetMapping("/api/workstreams/{workstreamId}/facts")
    public List<FactDto> list(@PathVariable UUID workstreamId,
                               @RequestParam(defaultValue = "false") boolean includeSuperseded,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf,
                               @AuthenticationPrincipal User currentUser) {
        if (asOf != null) {
            return factService.listAsOf(workstreamId, asOf, currentUser.getId());
        }
        return factService.list(workstreamId, includeSuperseded, currentUser.getId());
    }

    @GetMapping("/api/facts/{id}/history")
    public List<FactDto> history(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return factService.history(id, currentUser.getId());
    }

    @PostMapping("/api/workstreams/{workstreamId}/facts")
    @ResponseStatus(HttpStatus.CREATED)
    public FactDto create(@PathVariable UUID workstreamId, @Valid @RequestBody CreateFactRequest request,
                           @AuthenticationPrincipal User currentUser) {
        return factService.create(workstreamId, request, currentUser);
    }

    @GetMapping("/api/facts/{id}")
    public FactDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return factService.get(id, currentUser.getId());
    }

    @PutMapping("/api/facts/{id}")
    public FactDto supersede(@PathVariable UUID id, @Valid @RequestBody UpdateFactRequest request,
                              @AuthenticationPrincipal User currentUser) {
        return factService.supersede(id, request, currentUser);
    }

    @PostMapping("/api/facts/{id}/verify")
    public FactDto verify(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return factService.verify(id, currentUser);
    }

    @PostMapping("/api/facts/{id}/evidence-links")
    public FactDto linkEvidence(@PathVariable UUID id, @RequestBody LinkEvidenceRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        return factService.linkEvidence(id, request.evidenceId(), currentUser.getId());
    }

    @DeleteMapping("/api/facts/{id}/evidence-links/{evidenceId}")
    public FactDto unlinkEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId,
                                   @AuthenticationPrincipal User currentUser) {
        return factService.unlinkEvidence(id, evidenceId, currentUser.getId());
    }

    public record LinkEvidenceRequest(UUID evidenceId) {
    }
}
