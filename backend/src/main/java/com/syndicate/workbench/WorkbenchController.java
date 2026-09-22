package com.syndicate.workbench;

import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/api/transactions/{id}/workbench")
    public WorkbenchDto workbench(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return workbenchService.workbench(id, currentUser.getId());
    }
}
