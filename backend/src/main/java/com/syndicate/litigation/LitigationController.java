package com.syndicate.litigation;

import com.syndicate.litigation.dto.LitigationCaseDto;
import com.syndicate.litigation.dto.LitigationSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/litigation")
public class LitigationController {

    private final LitigationService litigationService;

    public LitigationController(LitigationService litigationService) {
        this.litigationService = litigationService;
    }

    @GetMapping
    public ResponseEntity<LitigationSummaryDto> getLitigationSummary(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(litigationService.getLitigationSummary(transactionId));
    }

    @PostMapping("/cases")
    public ResponseEntity<LitigationCaseDto> recordCase(
            @PathVariable UUID transactionId,
            @RequestBody LitigationCaseDto dto) {
        return ResponseEntity.ok(litigationService.recordLitigationCase(transactionId, dto));
    }
}
