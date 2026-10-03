package com.syndicate.lifecycle;

import com.syndicate.lifecycle.dto.RecordMilestoneRequest;
import com.syndicate.lifecycle.dto.StageDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class LifecycleController {

    private final LifecycleService lifecycleService;

    public LifecycleController(LifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/api/transactions/{id}/stage")
    public StageDto stage(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return lifecycleService.stage(id, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/milestones/{type}")
    public StageDto record(@PathVariable UUID id, @PathVariable MilestoneType type,
                           @Valid @RequestBody RecordMilestoneRequest request,
                           @AuthenticationPrincipal User currentUser) {
        return lifecycleService.record(id, type, request, currentUser);
    }
}
