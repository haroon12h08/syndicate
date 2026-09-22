package com.syndicate.conflict;

import com.syndicate.conflict.dto.ConflictDto;
import com.syndicate.conflict.dto.ResolveConflictRequest;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ConflictController {

    private final ConflictService conflictService;

    public ConflictController(ConflictService conflictService) {
        this.conflictService = conflictService;
    }

    @GetMapping("/api/transactions/{id}/conflicts")
    public List<ConflictDto> list(@PathVariable UUID id, @RequestParam(required = false) ConflictStatus status,
                                  @AuthenticationPrincipal User currentUser) {
        return conflictService.list(id, status, currentUser.getId());
    }

    @GetMapping("/api/conflicts/{id}")
    public ConflictDto get(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return conflictService.get(id, currentUser.getId());
    }

    @PostMapping("/api/conflicts/{id}/resolve")
    public ConflictDto resolve(@PathVariable UUID id, @Valid @RequestBody ResolveConflictRequest request,
                               @AuthenticationPrincipal User currentUser) {
        return conflictService.resolve(id, request, currentUser);
    }
}
