package com.syndicate.rpt;

import com.syndicate.rpt.dto.RelatedPartyDto;
import com.syndicate.rpt.dto.RelatedPartyTransactionDto;
import com.syndicate.rpt.dto.RptSummaryDto;
import com.syndicate.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/rpt")
public class RelatedPartyController {

    private final RelatedPartyService relatedPartyService;

    public RelatedPartyController(RelatedPartyService relatedPartyService) {
        this.relatedPartyService = relatedPartyService;
    }

    @GetMapping
    public ResponseEntity<RptSummaryDto> getRptSummary(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(relatedPartyService.getRptSummary(transactionId));
    }

    @PostMapping("/parties")
    public ResponseEntity<RelatedPartyDto> createParty(
            @PathVariable UUID transactionId,
            @RequestBody RelatedPartyDto dto) {
        return ResponseEntity.ok(relatedPartyService.createRelatedParty(transactionId, dto));
    }

    @PostMapping("/parties/{partyId}/confirm")
    public ResponseEntity<RelatedPartyDto> confirmParty(
            @PathVariable UUID transactionId,
            @PathVariable UUID partyId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(relatedPartyService.confirmSuggestedParty(partyId, user));
    }

    @PostMapping("/transactions")
    public ResponseEntity<RelatedPartyTransactionDto> recordTransaction(
            @PathVariable UUID transactionId,
            @RequestBody RelatedPartyTransactionDto dto) {
        return ResponseEntity.ok(relatedPartyService.recordTransaction(transactionId, dto));
    }
}
