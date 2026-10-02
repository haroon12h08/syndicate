package com.syndicate.filing;

import com.syndicate.filing.dto.ApprovalDto;
import com.syndicate.filing.dto.FilingStatusDto;
import com.syndicate.user.User;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class FilingController {

    private final FilingService filingService;

    public FilingController(FilingService filingService) {
        this.filingService = filingService;
    }

    /** Approvals outstanding, blockers, and the package once it exists. */
    @GetMapping("/api/transactions/{id}/filing")
    public FilingStatusDto status(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return filingService.status(id, currentUser.getId());
    }

    @PostMapping("/api/drhp/versions/{id}/approvals")
    public ApprovalDto approve(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return filingService.approve(id, currentUser);
    }

    @PostMapping("/api/drhp/versions/{id}/filing-package")
    public FilingStatusDto assemble(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return filingService.assemble(id, currentUser);
    }

    @GetMapping("/api/drhp/versions/{id}/filing-package")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        FilingPackage filingPackage = filingService.packageFor(id, currentUser.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filingPackage.getFileName() + "\"")
                .header("X-Package-Sha256", filingPackage.getSha256())
                .body(filingService.load(filingPackage));
    }
}
