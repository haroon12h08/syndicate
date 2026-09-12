package com.syndicate.drhp;

import com.syndicate.drhp.dto.CompileResultDto;
import com.syndicate.drhp.dto.DisclosureDto;
import com.syndicate.drhp.dto.DrhpDocumentDto;
import com.syndicate.drhp.dto.SaveDisclosureRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DrhpController {

    private final DrhpService drhpService;

    public DrhpController(DrhpService drhpService) {
        this.drhpService = drhpService;
    }

    @GetMapping("/api/transactions/{id}/disclosures")
    public List<DisclosureDto> listDisclosures(@PathVariable UUID id,
                                                @AuthenticationPrincipal User currentUser) {
        return drhpService.listDisclosures(id, currentUser.getId());
    }

    @PostMapping("/api/transactions/{id}/disclosures")
    @ResponseStatus(HttpStatus.CREATED)
    public DisclosureDto createDisclosure(@PathVariable UUID id,
                                           @Valid @RequestBody SaveDisclosureRequest request,
                                           @AuthenticationPrincipal User currentUser) {
        return drhpService.createDisclosure(id, request, currentUser);
    }

    @PutMapping("/api/disclosures/{id}")
    public DisclosureDto updateDisclosure(@PathVariable UUID id,
                                           @Valid @RequestBody SaveDisclosureRequest request,
                                           @AuthenticationPrincipal User currentUser) {
        return drhpService.updateDisclosure(id, request, currentUser);
    }

    @PostMapping("/api/disclosures/{id}/fact-links")
    public DisclosureDto linkFact(@PathVariable UUID id, @RequestBody LinkFactRequest request,
                                   @AuthenticationPrincipal User currentUser) {
        return drhpService.linkFact(id, request.factId(), currentUser);
    }

    @DeleteMapping("/api/disclosures/{id}/fact-links/{factId}")
    public DisclosureDto unlinkFact(@PathVariable UUID id, @PathVariable UUID factId,
                                     @AuthenticationPrincipal User currentUser) {
        return drhpService.unlinkFact(id, factId, currentUser);
    }

    @PostMapping("/api/transactions/{id}/drhp/compile")
    public CompileResultDto compile(@PathVariable UUID id,
                                     @RequestParam(defaultValue = "DRAFT_PREVIEW") CompileMode mode,
                                     @AuthenticationPrincipal User currentUser) {
        return drhpService.compile(id, currentUser, mode);
    }

    @GetMapping("/api/transactions/{id}/drhp")
    public DrhpDocumentDto latest(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return drhpService.latest(id, currentUser.getId());
    }

    public record LinkFactRequest(UUID factId) {
    }
}
