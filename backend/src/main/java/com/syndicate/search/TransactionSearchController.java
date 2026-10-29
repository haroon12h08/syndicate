package com.syndicate.search;

import com.syndicate.search.dto.TransactionSearchResultsDto;
import com.syndicate.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions/{transactionId}/search")
public class TransactionSearchController {

    private final TransactionSearchService searchService;

    public TransactionSearchController(TransactionSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<TransactionSearchResultsDto> search(
            @PathVariable UUID transactionId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) String types,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @AuthenticationPrincipal User user
    ) {
        TransactionSearchResultsDto results = searchService.search(transactionId, q, types, limit, user.getId());
        return ResponseEntity.ok(results);
    }
}
