package com.syndicate.registry;

import com.syndicate.registry.dto.RegistryVerificationDto;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RegistryController {

    private final RegistryVerificationService registryVerificationService;

    public RegistryController(RegistryVerificationService registryVerificationService) {
        this.registryVerificationService = registryVerificationService;
    }

    @PostMapping("/api/companies/{id}/verify-registry")
    public RegistryVerificationDto verify(@PathVariable UUID id,
                                           @AuthenticationPrincipal User currentUser) {
        return registryVerificationService.verify(id, currentUser);
    }
}
