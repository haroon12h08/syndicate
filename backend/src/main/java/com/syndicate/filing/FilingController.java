package com.syndicate.filing;

import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.filing.dto.ApprovalDto;
import com.syndicate.filing.dto.FilingDiffDto;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class FilingController {

    private final FilingService filingService;
    private final ResubmissionService resubmissionService;
    private final DrhpDocumentRepository drhpRepository;

    public FilingController(FilingService filingService,
                            ResubmissionService resubmissionService,
                            DrhpDocumentRepository drhpRepository) {
        this.filingService = filingService;
        this.resubmissionService = resubmissionService;
        this.drhpRepository = drhpRepository;
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

    @GetMapping("/api/transactions/{id}/filing/diff")
    public FilingDiffDto diff(@PathVariable UUID id,
                               @RequestParam(required = false) UUID base,
                               @RequestParam(required = false) UUID target,
                               @AuthenticationPrincipal User currentUser) {
        if (base != null && target != null) {
            return resubmissionService.computeDiff(id, base, target, currentUser.getId());
        }
        if (target != null) {
            return resubmissionService.computeDiffAgainstPrevious(target, currentUser.getId());
        }
        var latest = drhpRepository.findFirstByTransactionIdOrderByVersionDesc(id)
                .orElseThrow(() -> new ResourceNotFoundException("No DRHP document found for transaction: " + id));
        return resubmissionService.computeDiffAgainstPrevious(latest.getId(), currentUser.getId());
    }

    @GetMapping("/api/drhp/versions/{id}/diff")
    public FilingDiffDto documentDiff(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return resubmissionService.computeDiffAgainstPrevious(id, currentUser.getId());
    }

    @GetMapping(value = "/api/drhp/versions/{id}/statement-of-changes", produces = "text/plain")
    public ResponseEntity<String> statementOfChangesText(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        String text = resubmissionService.getStatementOfChangesText(id, currentUser.getId());
        var doc = drhpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DRHP Document not found: " + id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"statement-of-changes-v" + doc.getVersion() + ".txt\"")
                .body(text);
    }
}
