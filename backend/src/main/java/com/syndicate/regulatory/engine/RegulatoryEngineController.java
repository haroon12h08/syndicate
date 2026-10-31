package com.syndicate.regulatory.engine;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.syndicate.regulatory.engine.dto.ControlEvaluationDto;
import com.syndicate.regulatory.engine.dto.CreateRequirementRequest;
import com.syndicate.regulatory.engine.dto.RequirementDto;
import com.syndicate.regulatory.engine.dto.RequirementTestCaseDto;
import com.syndicate.regulatory.engine.dto.RequirementVersionDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RegulatoryEngineController {

    private final RegulatoryEngineService engineService;
    private final RegulatoryGovernanceService governanceService;

    public RegulatoryEngineController(RegulatoryEngineService engineService,
                                      RegulatoryGovernanceService governanceService) {
        this.engineService = engineService;
        this.governanceService = governanceService;
    }

    @PostMapping("/transactions/{id}/regulatory/evaluate")
    public List<ControlEvaluationDto> evaluate(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return engineService.evaluateTransaction(id, currentUser);
    }

    @GetMapping("/transactions/{id}/regulatory/evaluations")
    public List<ControlEvaluationDto> getEvaluations(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return engineService.getEvaluations(id, currentUser.getId());
    }

    @GetMapping("/regulatory/requirements")
    public List<RequirementDto> listRequirements() {
        return governanceService.listRequirements();
    }

    @PostMapping("/regulatory/requirements")
    public ResponseEntity<RequirementVersionDto> createRequirement(@Valid @RequestBody CreateRequirementRequest request,
                                                                   @AuthenticationPrincipal User currentUser) {
        RequirementVersionDto dto = governanceService.createRequirement(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/regulatory/requirements/{versionId}/test")
    public List<RequirementTestCaseDto> testVersion(@PathVariable UUID versionId) {
        return governanceService.executeTestCases(versionId);
    }

    @PostMapping("/regulatory/requirements/{versionId}/submit")
    public RequirementVersionDto submitVersion(@PathVariable UUID versionId, @AuthenticationPrincipal User currentUser) {
        return governanceService.submitForApproval(versionId, currentUser);
    }

    @PostMapping("/regulatory/requirements/{versionId}/approve")
    public RequirementVersionDto approveVersion(@PathVariable UUID versionId, @AuthenticationPrincipal User currentUser) {
        return governanceService.approveRequirementVersion(versionId, currentUser);
    }
}
