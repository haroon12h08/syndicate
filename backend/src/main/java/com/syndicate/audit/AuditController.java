package com.syndicate.audit;

import com.syndicate.audit.dto.AuditEventDto;
import com.syndicate.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/transactions/{id}/audit")
    public List<AuditEventDto> listForTransaction(@PathVariable UUID id,
                                                   @AuthenticationPrincipal User currentUser) {
        return auditService.listForTransaction(id, currentUser.getId());
    }
}
