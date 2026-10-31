package com.syndicate.regulatory.engine;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.syndicate.regulatory.engine.dto.CreateExceptionRequest;
import com.syndicate.regulatory.engine.dto.RegulatoryExceptionDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RegulatoryExceptionController {

    private final RegulatoryExceptionService exceptionService;

    public RegulatoryExceptionController(RegulatoryExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @GetMapping("/transactions/{id}/regulatory-exceptions")
    public List<RegulatoryExceptionDto> listExceptions(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return exceptionService.listExceptions(id, currentUser.getId());
    }

    @PostMapping("/transactions/{id}/regulatory-exceptions")
    public ResponseEntity<RegulatoryExceptionDto> requestException(@PathVariable UUID id,
                                                                   @Valid @RequestBody CreateExceptionRequest request,
                                                                   @AuthenticationPrincipal User currentUser) {
        RegulatoryExceptionDto dto = exceptionService.requestException(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/regulatory-exceptions/{exceptionId}/approve")
    public RegulatoryExceptionDto approveException(@PathVariable UUID exceptionId, @AuthenticationPrincipal User currentUser) {
        return exceptionService.approveException(exceptionId, currentUser);
    }

    @PostMapping("/regulatory-exceptions/{exceptionId}/reject")
    public RegulatoryExceptionDto rejectException(@PathVariable UUID exceptionId,
                                                  @RequestParam(required = false, defaultValue = "Rejected by reviewer") String reason,
                                                  @AuthenticationPrincipal User currentUser) {
        return exceptionService.rejectException(exceptionId, reason, currentUser);
    }
}
