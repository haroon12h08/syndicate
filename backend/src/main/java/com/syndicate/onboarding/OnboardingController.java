package com.syndicate.onboarding;

import com.syndicate.transaction.dto.TransactionDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /** Opens a transaction, its company and its diligence areas in one step. */
    @PostMapping("/api/transactions/start")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDto start(@Valid @RequestBody StartTransactionRequest request,
                                @AuthenticationPrincipal User currentUser) {
        return onboardingService.start(request, currentUser);
    }
}
