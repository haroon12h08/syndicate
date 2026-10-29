package com.syndicate.ai.drafting;

import com.syndicate.ai.drafting.dto.*;
import com.syndicate.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AiDraftingController {

    private final AiDraftingService draftingService;

    public AiDraftingController(AiDraftingService draftingService) {
        this.draftingService = draftingService;
    }

    @PostMapping("/transactions/{transactionId}/drhp/disclosures/draft-ai")
    public ResponseEntity<DraftDisclosureResponse> draftDisclosure(
            @PathVariable UUID transactionId,
            @RequestBody DraftDisclosureRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(draftingService.draftDisclosure(transactionId, request, user));
    }

    @PostMapping("/observations/{observationId}/draft-ai")
    public ResponseEntity<DraftObservationResponse> draftObservationResponse(
            @PathVariable UUID observationId,
            @RequestBody(required = false) DraftObservationResponseRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(draftingService.draftObservationResponse(observationId, request, user));
    }
}
