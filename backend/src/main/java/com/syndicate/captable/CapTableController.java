package com.syndicate.captable;

import com.syndicate.captable.dto.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/captable")
public class CapTableController {

    private final CapTableService capTableService;

    public CapTableController(CapTableService capTableService) {
        this.capTableService = capTableService;
    }

    @GetMapping
    public ResponseEntity<CapTableSummaryDto> getCapTable(
            @PathVariable UUID transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(capTableService.getSummary(transactionId, asOf));
    }

    @PostMapping("/security-classes")
    public ResponseEntity<SecurityClassDto> createSecurityClass(
            @PathVariable UUID transactionId,
            @RequestBody SecurityClassDto dto) {
        return ResponseEntity.ok(capTableService.createSecurityClass(transactionId, dto));
    }

    @PostMapping("/holders")
    public ResponseEntity<CapTableHolderDto> createHolder(
            @PathVariable UUID transactionId,
            @RequestBody CapTableHolderDto dto) {
        return ResponseEntity.ok(capTableService.createHolder(transactionId, dto));
    }

    @PostMapping("/transactions")
    public ResponseEntity<ShareTransactionDto> recordTransaction(
            @PathVariable UUID transactionId,
            @RequestBody ShareTransactionDto dto) {
        return ResponseEntity.ok(capTableService.recordShareTransaction(transactionId, dto));
    }

    @PutMapping("/ipo-parameters")
    public ResponseEntity<IpoIssueParametersDto> updateIpoParameters(
            @PathVariable UUID transactionId,
            @RequestBody IpoIssueParametersDto dto) {
        return ResponseEntity.ok(capTableService.saveIpoParameters(transactionId, dto));
    }

    @PostMapping("/encumbrances")
    public ResponseEntity<ShareEncumbranceDto> recordEncumbrance(
            @PathVariable UUID transactionId,
            @RequestBody ShareEncumbranceDto dto) {
        return ResponseEntity.ok(capTableService.recordEncumbrance(transactionId, dto));
    }

    public record RecalculateLockInsRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate allotmentDate,
            Map<UUID, LockInExemption> exemptions
    ) {}

    @PostMapping("/lock-ins/recalculate")
    public ResponseEntity<List<ShareLockInDto>> recalculateLockIns(
            @PathVariable UUID transactionId,
            @RequestBody(required = false) RecalculateLockInsRequest request) {
        LocalDate allotmentDate = request != null ? request.allotmentDate() : null;
        Map<UUID, LockInExemption> exemptions = request != null ? request.exemptions() : null;
        return ResponseEntity.ok(capTableService.calculateAndPersistLockIns(transactionId, allotmentDate, exemptions));
    }
}
