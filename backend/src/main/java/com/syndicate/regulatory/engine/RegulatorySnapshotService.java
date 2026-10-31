package com.syndicate.regulatory.engine;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.regulatory.engine.dto.RequirementVersionDto;
import com.syndicate.regulatory.engine.dto.RegulatorySnapshotDto;
import com.syndicate.regulatory.engine.dto.SnapshotDriftDto;
import com.syndicate.regulatory.engine.dto.SnapshotDriftDto.OutdatedRequirementDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages transaction regulatory snapshots and drift detection (spec §13; plan D3).
 */
@Service
public class RegulatorySnapshotService {

    private final RegulatorySnapshotRepository snapshotRepository;
    private final RegulatorySnapshotItemRepository snapshotItemRepository;
    private final RequirementVersionRepository requirementVersionRepository;
    private final TransactionService transactionService;
    private final RegulatoryEngineService engineService;
    private final AuditService auditService;

    public RegulatorySnapshotService(RegulatorySnapshotRepository snapshotRepository,
                                     RegulatorySnapshotItemRepository snapshotItemRepository,
                                     RequirementVersionRepository requirementVersionRepository,
                                     TransactionService transactionService,
                                     RegulatoryEngineService engineService,
                                     AuditService auditService) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotItemRepository = snapshotItemRepository;
        this.requirementVersionRepository = requirementVersionRepository;
        this.transactionService = transactionService;
        this.engineService = engineService;
        this.auditService = auditService;
    }

    @Transactional
    public RegulatorySnapshotDto getActiveSnapshot(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        RegulatorySnapshot snapshot = snapshotRepository
                .findFirstByTransactionIdAndActiveTrueOrderByPinnedAtDesc(transactionId)
                .orElseGet(() -> autoPinDefaultSnapshot(transactionId));

        List<RequirementVersionDto> items = snapshotItemRepository.findBySnapshotId(snapshot.getId()).stream()
                .map(item -> RequirementVersionDto.from(item.getRequirementVersion(), List.of()))
                .toList();

        return RegulatorySnapshotDto.from(snapshot, items);
    }

    private RegulatorySnapshot autoPinDefaultSnapshot(UUID transactionId) {
        Transaction transaction = transactionService.findTransaction(transactionId);
        RegulatorySnapshot newSnapshot = snapshotRepository.save(new RegulatorySnapshot(
                transaction,
                "SEBI ICDR 2025.1 Standard Snapshot",
                LocalDate.now(),
                null,
                "Default statutory snapshot pinned upon transaction initialization"
        ));

        List<RequirementVersion> activeVersions = requirementVersionRepository.findByStatus("ACTIVE");
        if (activeVersions.isEmpty()) {
            activeVersions = requirementVersionRepository.findAll();
        }
        for (RequirementVersion v : activeVersions) {
            snapshotItemRepository.save(new RegulatorySnapshotItem(newSnapshot, v));
        }
        return newSnapshot;
    }

    @Transactional(readOnly = true)
    public SnapshotDriftDto detectDrift(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        Optional<RegulatorySnapshot> optSnapshot = snapshotRepository
                .findFirstByTransactionIdAndActiveTrueOrderByPinnedAtDesc(transactionId);

        if (optSnapshot.isEmpty()) {
            return new SnapshotDriftDto(false, null, null, 0, List.of());
        }

        RegulatorySnapshot snapshot = optSnapshot.get();
        List<RegulatorySnapshotItem> pinnedItems = snapshotItemRepository.findBySnapshotId(snapshot.getId());

        // Latest active version for each requirement
        List<RequirementVersion> activeVersions = requirementVersionRepository.findByStatus("ACTIVE");
        Map<UUID, RequirementVersion> latestActiveByReq = activeVersions.stream()
                .collect(Collectors.toMap(
                        v -> v.getRequirement().getId(),
                        v -> v,
                        (v1, v2) -> v1.getVersionNumber() > v2.getVersionNumber() ? v1 : v2
                ));

        List<OutdatedRequirementDto> outdatedList = new ArrayList<>();
        for (RegulatorySnapshotItem item : pinnedItems) {
            RequirementVersion pinned = item.getRequirementVersion();
            RequirementVersion latest = latestActiveByReq.get(pinned.getRequirement().getId());
            if (latest != null && latest.getVersionNumber() > pinned.getVersionNumber()) {
                outdatedList.add(new OutdatedRequirementDto(
                        pinned.getRequirement().getCode(),
                        pinned.getRequirement().getTitle(),
                        pinned.getVersionNumber(),
                        latest.getVersionNumber(),
                        latest.getEffectiveFrom(),
                        "Version upgraded from v" + pinned.getVersionNumber() + " to v" + latest.getVersionNumber()
                                + " with citation: " + latest.getCitation()
                ));
            }
        }

        return new SnapshotDriftDto(
                !outdatedList.isEmpty(),
                snapshot.getId(),
                snapshot.getEffectiveDate(),
                outdatedList.size(),
                outdatedList
        );
    }

    @Transactional
    public RegulatorySnapshotDto upgradeSnapshot(UUID transactionId, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);

        // Deactivate existing snapshot
        snapshotRepository.findFirstByTransactionIdAndActiveTrueOrderByPinnedAtDesc(transactionId)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    snapshotRepository.save(existing);
                });

        // Collect latest active versions for each requirement
        List<RequirementVersion> activeVersions = requirementVersionRepository.findByStatus("ACTIVE");
        Map<UUID, RequirementVersion> latestActiveByReq = activeVersions.stream()
                .collect(Collectors.toMap(
                        v -> v.getRequirement().getId(),
                        v -> v,
                        (v1, v2) -> v1.getVersionNumber() > v2.getVersionNumber() ? v1 : v2
                ));

        RegulatorySnapshot newSnapshot = snapshotRepository.save(new RegulatorySnapshot(
                transaction,
                "Upgraded Regulatory Snapshot (Effective " + LocalDate.now() + ")",
                LocalDate.now(),
                caller,
                "Upgraded to latest statutory standards by " + caller.getFullName()
        ));

        List<RequirementVersionDto> itemsDto = new ArrayList<>();
        for (RequirementVersion latestVer : latestActiveByReq.values()) {
            RegulatorySnapshotItem item = snapshotItemRepository.save(new RegulatorySnapshotItem(newSnapshot, latestVer));
            itemsDto.add(RequirementVersionDto.from(latestVer, List.of()));
        }

        auditService.record(transactionId, caller, AuditAction.REGULATORY_SNAPSHOT_UPGRADED,
                "RegulatorySnapshot", newSnapshot.getId(),
                "Upgraded regulatory snapshot to " + itemsDto.size() + " active requirements", null, null, null);

        // Immediately evaluate against newly pinned snapshot
        engineService.evaluateTransaction(transactionId, caller);

        return RegulatorySnapshotDto.from(newSnapshot, itemsDto);
    }
}
