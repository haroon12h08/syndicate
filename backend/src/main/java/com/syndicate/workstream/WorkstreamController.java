package com.syndicate.workstream;

import com.syndicate.user.User;
import com.syndicate.workstream.dto.CreateWorkstreamRequest;
import com.syndicate.workstream.dto.UpdateWorkstreamRequest;
import com.syndicate.workstream.dto.WorkstreamDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class WorkstreamController {

    private final WorkstreamService workstreamService;

    public WorkstreamController(WorkstreamService workstreamService) {
        this.workstreamService = workstreamService;
    }

    @GetMapping("/api/transactions/{transactionId}/workstreams")
    public List<WorkstreamDto> list(@PathVariable UUID transactionId, @AuthenticationPrincipal User currentUser) {
        return workstreamService.listForTransaction(transactionId, currentUser.getId());
    }

    @PostMapping("/api/transactions/{transactionId}/workstreams")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkstreamDto create(@PathVariable UUID transactionId,
                                 @Valid @RequestBody CreateWorkstreamRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        return workstreamService.create(transactionId, request, currentUser.getId());
    }

    @GetMapping("/api/workstreams/{id}")
    public WorkstreamDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return workstreamService.get(id, currentUser.getId());
    }

    @PutMapping("/api/workstreams/{id}")
    public WorkstreamDto update(@PathVariable UUID id, @RequestBody UpdateWorkstreamRequest request,
                                 @AuthenticationPrincipal User currentUser) {
        return workstreamService.update(id, request, currentUser.getId());
    }
}
