package com.syndicate.contract;

import com.syndicate.contract.dto.ContractSummaryDto;
import com.syndicate.contract.dto.MaterialContractDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/contracts")
public class MaterialContractController {

    private final MaterialContractService materialContractService;

    public MaterialContractController(MaterialContractService materialContractService) {
        this.materialContractService = materialContractService;
    }

    @GetMapping
    public ResponseEntity<ContractSummaryDto> getContractSummary(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(materialContractService.getContractSummary(transactionId));
    }

    @PostMapping
    public ResponseEntity<MaterialContractDto> recordContract(
            @PathVariable UUID transactionId,
            @RequestBody MaterialContractDto dto) {
        return ResponseEntity.ok(materialContractService.recordMaterialContract(transactionId, dto));
    }
}
