package com.syndicate.candidatefact;

import com.syndicate.candidatefact.dto.AcceptCandidateFactRequest;
import com.syndicate.candidatefact.dto.CandidateFactDto;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CandidateFactController {

    private final CandidateFactService candidateFactService;

    public CandidateFactController(CandidateFactService candidateFactService) {
        this.candidateFactService = candidateFactService;
    }

    @GetMapping("/api/workstreams/{workstreamId}/candidate-facts")
    public List<CandidateFactDto> list(@PathVariable UUID workstreamId,
                                        @RequestParam(required = false) CandidateFactStatus status,
                                        @AuthenticationPrincipal User currentUser) {
        return candidateFactService.list(workstreamId, status, currentUser.getId());
    }

    @GetMapping("/api/candidate-facts/{id}")
    public CandidateFactDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return candidateFactService.get(id, currentUser.getId());
    }

    @PostMapping("/api/candidate-facts/{id}/accept")
    public CandidateFactDto accept(@PathVariable UUID id,
                                    @RequestBody(required = false) AcceptCandidateFactRequest request,
                                    @AuthenticationPrincipal User currentUser) {
        AcceptCandidateFactRequest body = request != null
                ? request : new AcceptCandidateFactRequest(null, null, null, null, null);
        return candidateFactService.accept(id, body, currentUser);
    }

    @PostMapping("/api/candidate-facts/{id}/reject")
    public CandidateFactDto reject(@PathVariable UUID id,
                                    @RequestBody(required = false) RejectCandidateFactRequest request,
                                    @AuthenticationPrincipal User currentUser) {
        RejectCandidateFactRequest body = request != null ? request : new RejectCandidateFactRequest(null);
        return candidateFactService.reject(id, body, currentUser);
    }
}
