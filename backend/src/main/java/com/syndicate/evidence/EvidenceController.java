package com.syndicate.evidence;

import com.syndicate.evidence.dto.EvidenceDto;
import com.syndicate.user.User;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/api/transactions/{transactionId}/evidence")
    public List<EvidenceDto> listForTransaction(@PathVariable UUID transactionId,
                                                 @AuthenticationPrincipal User currentUser) {
        return evidenceService.listForTransaction(transactionId, currentUser.getId());
    }

    @GetMapping("/api/workstreams/{workstreamId}/evidence")
    public List<EvidenceDto> list(@PathVariable UUID workstreamId, @AuthenticationPrincipal User currentUser) {
        return evidenceService.list(workstreamId, currentUser.getId());
    }

    @PostMapping(value = "/api/workstreams/{workstreamId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceDto upload(@PathVariable UUID workstreamId,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam("documentType") EvidenceDocumentType documentType,
                               @AuthenticationPrincipal User currentUser) {
        return evidenceService.upload(workstreamId, file, documentType, currentUser);
    }

    @GetMapping("/api/evidence/{id}")
    public EvidenceDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return evidenceService.get(id, currentUser.getId());
    }

    @GetMapping("/api/evidence/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        EvidenceDto meta = evidenceService.get(id, currentUser.getId());
        Resource resource = evidenceService.download(id, currentUser.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meta.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.fileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/api/evidence/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        evidenceService.delete(id, currentUser.getId());
    }

    @PostMapping("/api/evidence/{id}/reprocess")
    public EvidenceDto reprocess(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return evidenceService.reprocess(id, currentUser.getId());
    }

    @GetMapping("/api/evidence/{id}/pages/{pageNumber}/image")
    public ResponseEntity<Resource> pageImage(@PathVariable UUID id, @PathVariable int pageNumber,
                                               @AuthenticationPrincipal User currentUser) {
        Resource resource = evidenceService.loadPageImage(id, pageNumber, currentUser.getId());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(resource);
    }
}
