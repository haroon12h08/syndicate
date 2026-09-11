package com.syndicate.issue;

import com.syndicate.issue.dto.CreateIssueRequest;
import com.syndicate.issue.dto.IssueDto;
import com.syndicate.issue.dto.UpdateIssueRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping("/api/workstreams/{workstreamId}/issues")
    public List<IssueDto> list(@PathVariable UUID workstreamId, @AuthenticationPrincipal User currentUser) {
        return issueService.list(workstreamId, currentUser.getId());
    }

    @PostMapping("/api/workstreams/{workstreamId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueDto create(@PathVariable UUID workstreamId, @Valid @RequestBody CreateIssueRequest request,
                            @AuthenticationPrincipal User currentUser) {
        return issueService.create(workstreamId, request, currentUser);
    }

    @GetMapping("/api/issues/{id}")
    public IssueDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return issueService.get(id, currentUser.getId());
    }

    @PutMapping("/api/issues/{id}")
    public IssueDto update(@PathVariable UUID id, @Valid @RequestBody UpdateIssueRequest request,
                            @AuthenticationPrincipal User currentUser) {
        return issueService.update(id, request, currentUser.getId());
    }
}
