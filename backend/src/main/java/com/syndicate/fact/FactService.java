package com.syndicate.fact;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.fact.dto.CreateFactRequest;
import com.syndicate.fact.dto.FactDto;
import com.syndicate.fact.dto.FactTraceDto;
import com.syndicate.fact.dto.UpdateFactRequest;
import com.syndicate.permission.PermissionService;
import com.syndicate.drhp.ChangePropagationService;
import com.syndicate.regulatory.ReadinessService;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamService;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FactService {

    private final FactRepository factRepository;
    private final EvidenceRepository evidenceRepository;
    private final WorkstreamService workstreamService;
    private final PermissionService permissionService;
    private final ReadinessService readinessService;
    private final ChangePropagationService changePropagationService;
    private final CandidateFactRepository candidateFactRepository;

    public FactService(FactRepository factRepository, EvidenceRepository evidenceRepository,
                        WorkstreamService workstreamService, PermissionService permissionService,
                        ReadinessService readinessService,
                        ChangePropagationService changePropagationService,
                        CandidateFactRepository candidateFactRepository) {
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.workstreamService = workstreamService;
        this.permissionService = permissionService;
        this.readinessService = readinessService;
        this.changePropagationService = changePropagationService;
        this.candidateFactRepository = candidateFactRepository;
    }

    @Transactional
    public FactDto create(UUID workstreamId, CreateFactRequest request, User caller) {
        workstreamService.requireAccess(workstreamId, caller.getId());
        Workstream workstream = workstreamService.findWorkstream(workstreamId);
        Instant validFrom = request.validFrom() != null ? request.validFrom() : Instant.now();
        requireOrderedValidityWindow(validFrom, request.validTo());
        Fact fact = new Fact(workstream, request.label(), request.value(), request.unit(), request.period(),
                null, 1, caller, validFrom, request.validTo());
        return FactDto.from(factRepository.save(fact));
    }

    public List<FactDto> list(UUID workstreamId, boolean includeSuperseded, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        List<Fact> facts = includeSuperseded
                ? factRepository.findByWorkstreamId(workstreamId)
                : factRepository.findByWorkstreamIdAndStatusNot(workstreamId, FactStatus.SUPERSEDED);
        return facts.stream().map(FactDto::from).toList();
    }

    /**
     * Reconstructs what the workstream's facts looked like to Syndicate at a past instant:
     * the version of each fact that had been recorded by then and had not yet been replaced.
     */
    public List<FactDto> listAsOf(UUID workstreamId, Instant asOf, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        return factRepository.findByWorkstreamId(workstreamId).stream()
                .filter(fact -> wasSystemCurrentAt(fact, asOf))
                .map(FactDto::from)
                .toList();
    }

    private boolean wasSystemCurrentAt(Fact fact, Instant asOf) {
        if (fact.getCreatedAt().isAfter(asOf)) {
            return false;
        }
        return fact.getSystemSupersededAt() == null || fact.getSystemSupersededAt().isAfter(asOf);
    }

    /**
     * Returns every version of the fact's supersede chain, oldest first, regardless of which
     * version's id was asked for.
     */
    public List<FactDto> history(UUID factId, UUID callerId) {
        Fact fact = findFact(factId);
        workstreamService.requireAccess(fact.getWorkstream().getId(), callerId);

        Fact genesis = fact;
        while (genesis.getSupersedesFact() != null) {
            genesis = genesis.getSupersedesFact();
        }

        List<FactDto> chain = new ArrayList<>();
        for (Fact version = genesis; version != null;
             version = factRepository.findBySupersedesFactId(version.getId()).orElse(null)) {
            chain.add(FactDto.from(version));
        }
        return chain;
    }

    private void requireOrderedValidityWindow(Instant validFrom, Instant validTo) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new BadRequestException("validTo must not be before validFrom");
        }
    }

    /** Follows a fact back to its evidence and, when it came from a document, its page region. */
    public FactTraceDto trace(UUID factId, UUID callerId) {
        Fact fact = findFact(factId);
        workstreamService.requireAccess(fact.getWorkstream().getId(), callerId);

        List<FactTraceDto.EvidenceRefDto> evidence = fact.getEvidence().stream()
                .map(e -> new FactTraceDto.EvidenceRefDto(e.getId(), e.getFileName(),
                        e.getDocumentType() != null ? e.getDocumentType().name() : null))
                .toList();

        FactTraceDto.SpatialOriginDto origin = candidateFactRepository.findByResultingFactId(factId)
                .map(this::toOrigin)
                .orElse(null);

        return new FactTraceDto(FactDto.from(fact), evidence, origin);
    }

    private FactTraceDto.SpatialOriginDto toOrigin(CandidateFact candidate) {
        return new FactTraceDto.SpatialOriginDto(
                candidate.getId(),
                candidate.getEvidence().getId(),
                candidate.getEvidence().getFileName(),
                candidate.getPageNumber(),
                candidate.getBboxX(),
                candidate.getBboxY(),
                candidate.getBboxWidth(),
                candidate.getBboxHeight(),
                candidate.getPageImageWidth(),
                candidate.getPageImageHeight(),
                candidate.getSource() != null ? candidate.getSource().name() : null,
                candidate.getReviewedByUser() != null ? candidate.getReviewedByUser().getFullName() : null,
                candidate.getReviewedAt());
    }

    public FactDto get(UUID factId, UUID callerId) {
        Fact fact = findFact(factId);
        workstreamService.requireAccess(fact.getWorkstream().getId(), callerId);
        return FactDto.from(fact);
    }

    @Transactional
    public FactDto supersede(UUID factId, UpdateFactRequest request, User caller) {
        Fact oldFact = findFact(factId);
        workstreamService.requireAccess(oldFact.getWorkstream().getId(), caller.getId());
        if (oldFact.getStatus() == FactStatus.SUPERSEDED) {
            throw new BadRequestException("This fact has already been superseded");
        }
        Instant now = Instant.now();
        Instant validFrom = request.validFrom() != null ? request.validFrom() : now;
        requireOrderedValidityWindow(validFrom, request.validTo());

        oldFact.markSuperseded(now, validFrom);
        Fact newFact = new Fact(oldFact.getWorkstream(), request.label(), request.value(), request.unit(),
                request.period(), oldFact, oldFact.getVersion() + 1, caller, validFrom, request.validTo());
        Fact saved = factRepository.save(newFact);
        UUID supersededId = oldFact.getId();
        String supersededLabel = oldFact.getLabel();
        scheduleAfterCommit(() -> {
            changePropagationService.propagateFactSuperseded(supersededId, supersededLabel);
            readinessService.reevaluateQuietly(transactionId(oldFact), caller);
        });
        return FactDto.from(saved);
    }

    @Transactional
    public FactDto verify(UUID factId, User caller) {
        Fact fact = findFact(factId);
        Workstream workstream = fact.getWorkstream();
        workstreamService.requireAccess(workstream.getId(), caller.getId());
        if (workstream.getType() == WorkstreamType.FINANCIAL_DUE_DILIGENCE) {
            UUID transactionId = workstream.getTransaction().getId();
            TransactionRole role = permissionService.requireTransactionMembershipRole(transactionId, caller.getId());
            if (role != TransactionRole.AUDITOR && role != TransactionRole.ISSUER_ADMIN) {
                throw new ForbiddenException(
                        "Only an Auditor or the Issuer Admin can verify facts in Financial Due Diligence");
            }
        }
        fact.markVerified(caller, Instant.now());
        scheduleReadinessReevaluation(workstream.getTransaction().getId(), caller);
        return FactDto.from(fact);
    }

    /**
     * Readiness reflects committed facts, so the re-run is deferred until this transaction lands.
     * Running it inline would evaluate against data a separate transaction cannot see yet, and a
     * failure there would roll back the fact change that triggered it.
     */
    private void scheduleReadinessReevaluation(UUID transactionId, User caller) {
        scheduleAfterCommit(() -> readinessService.reevaluateQuietly(transactionId, caller));
    }

    private static UUID transactionId(Fact fact) {
        return fact.getWorkstream().getTransaction().getId();
    }

    private void scheduleAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Transactional
    public FactDto linkEvidence(UUID factId, UUID evidenceId, UUID callerId) {
        Fact fact = findFact(factId);
        workstreamService.requireAccess(fact.getWorkstream().getId(), callerId);
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found: " + evidenceId));
        if (!evidence.getWorkstream().getId().equals(fact.getWorkstream().getId())) {
            throw new BadRequestException("Evidence must belong to the same workstream as the fact");
        }
        fact.getEvidence().add(evidence);
        return FactDto.from(fact);
    }

    @Transactional
    public FactDto unlinkEvidence(UUID factId, UUID evidenceId, UUID callerId) {
        Fact fact = findFact(factId);
        workstreamService.requireAccess(fact.getWorkstream().getId(), callerId);
        fact.getEvidence().removeIf(e -> e.getId().equals(evidenceId));
        return FactDto.from(fact);
    }

    public Fact findFact(UUID factId) {
        return factRepository.findById(factId)
                .orElseThrow(() -> new ResourceNotFoundException("Fact not found: " + factId));
    }
}
