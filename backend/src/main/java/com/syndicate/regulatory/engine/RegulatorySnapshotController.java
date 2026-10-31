package com.syndicate.regulatory.engine;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.syndicate.regulatory.engine.dto.RegulatorySnapshotDto;
import com.syndicate.regulatory.engine.dto.SnapshotDriftDto;
import com.syndicate.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions/{transactionId}/regulatory-snapshot")
public class RegulatorySnapshotController {

    private final RegulatorySnapshotService snapshotService;

    public RegulatorySnapshotController(RegulatorySnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping
    public RegulatorySnapshotDto getSnapshot(@PathVariable UUID transactionId, @AuthenticationPrincipal User currentUser) {
        return snapshotService.getActiveSnapshot(transactionId, currentUser.getId());
    }

    @GetMapping("/drift")
    public SnapshotDriftDto getDrift(@PathVariable UUID transactionId, @AuthenticationPrincipal User currentUser) {
        return snapshotService.detectDrift(transactionId, currentUser.getId());
    }

    @PostMapping("/upgrade")
    public RegulatorySnapshotDto upgradeSnapshot(@PathVariable UUID transactionId, @AuthenticationPrincipal User currentUser) {
        return snapshotService.upgradeSnapshot(transactionId, currentUser);
    }
}
