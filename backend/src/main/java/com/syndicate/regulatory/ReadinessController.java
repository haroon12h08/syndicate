package com.syndicate.regulatory;

import com.syndicate.regulatory.dto.ReadinessDto;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ReadinessController {

    private final ReadinessService readinessService;

    public ReadinessController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/api/transactions/{id}/readiness")
    public ReadinessDto readiness(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return readinessService.getReadiness(id, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/readiness/evaluate")
    public ReadinessDto evaluate(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return readinessService.evaluate(id, currentUser);
    }
}
