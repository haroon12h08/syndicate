package com.syndicate.ai.suggestion;

import com.syndicate.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions/{transactionId}/suggestions")
public class AiSuggestionController {

    private final AiSuggestionService suggestionService;

    public AiSuggestionController(AiSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public ResponseEntity<List<AiSuggestionDto>> list(
            @PathVariable UUID transactionId,
            @RequestParam(required = false) SuggestionStatus status,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(suggestionService.list(transactionId, status, user.getId()));
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateSuggestionsResponse> generate(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(suggestionService.generate(transactionId, user));
    }

    @PostMapping("/{suggestionId}/accept")
    public ResponseEntity<AiSuggestionDto> accept(
            @PathVariable UUID transactionId,
            @PathVariable UUID suggestionId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(suggestionService.accept(transactionId, suggestionId, user));
    }

    @PostMapping("/{suggestionId}/dismiss")
    public ResponseEntity<AiSuggestionDto> dismiss(
            @PathVariable UUID transactionId,
            @PathVariable UUID suggestionId,
            @RequestBody(required = false) DismissSuggestionRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(suggestionService.dismiss(transactionId, suggestionId, request, user));
    }
}
