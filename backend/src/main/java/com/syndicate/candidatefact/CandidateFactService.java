package com.syndicate.candidatefact;

import com.syndicate.candidatefact.dto.AcceptCandidateFactRequest;
import com.syndicate.candidatefact.dto.CandidateFactDto;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.conflict.ConflictDetectionService;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.user.User;
import com.syndicate.workstream.WorkstreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CandidateFactService {

    private final CandidateFactRepository candidateFactRepository;
    private final FactRepository factRepository;
    private final WorkstreamService workstreamService;

    private final AuditService auditService;
    private final ConflictDetectionService conflictDetectionService;

    public CandidateFactService(CandidateFactRepository candidateFactRepository, FactRepository factRepository,
                                 WorkstreamService workstreamService,
                                 AuditService auditService,
                                 ConflictDetectionService conflictDetectionService) {
        this.candidateFactRepository = candidateFactRepository;
        this.factRepository = factRepository;
        this.workstreamService = workstreamService;
        this.auditService = auditService;
        this.conflictDetectionService = conflictDetectionService;
    }

    public List<CandidateFactDto> list(UUID workstreamId, CandidateFactStatus statusFilter, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        List<CandidateFact> candidates = statusFilter != null
                ? candidateFactRepository.findByWorkstreamIdAndStatus(workstreamId, statusFilter)
                : candidateFactRepository.findByWorkstreamId(workstreamId);
        return candidates.stream().map(CandidateFactDto::from).toList();
    }

    public CandidateFactDto get(UUID id, UUID callerId) {
        CandidateFact candidate = findCandidate(id);
        workstreamService.requireAccess(candidate.getWorkstream().getId(), callerId);
        return CandidateFactDto.from(candidate);
    }

    @Transactional
    public CandidateFactDto accept(UUID id, AcceptCandidateFactRequest request, User caller) {
        CandidateFact candidate = findCandidate(id);
        workstreamService.requireAccess(candidate.getWorkstream().getId(), caller.getId());
        requirePending(candidate);

        String label = request.label() != null ? request.label() : candidate.getLabel();
        String value = request.value() != null ? request.value() : candidate.getValue();
        String period = request.period() != null ? request.period() : candidate.getPeriod();

        Fact fact = new Fact(candidate.getWorkstream(), label, value, request.unit(), period, null, 1, caller,
                Instant.now(), null);
        fact.getEvidence().add(candidate.getEvidence());
        Fact savedFact = factRepository.save(fact);

        candidate.accept(caller, request.reviewNote(), savedFact);
        auditService.record(candidate.getWorkstream().getTransaction().getId(), caller,
                AuditAction.CANDIDATE_FACT_ACCEPTED, "Fact", savedFact.getId(),
                "Accepted extracted value " + label + " = " + value + " from "
                        + candidate.getEvidence().getFileName(),
                null, value, request.reviewNote());

        UUID txId = candidate.getWorkstream().getTransaction().getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    conflictDetectionService.detectQuietly(txId, caller);
                }
            });
        } else {
            conflictDetectionService.detectQuietly(txId, caller);
        }
        return CandidateFactDto.from(candidate);
    }

    @Transactional
    public CandidateFactDto reject(UUID id, RejectCandidateFactRequest request, User caller) {
        CandidateFact candidate = findCandidate(id);
        workstreamService.requireAccess(candidate.getWorkstream().getId(), caller.getId());
        requirePending(candidate);

        candidate.reject(caller, request.reviewNote());
        return CandidateFactDto.from(candidate);
    }

    private void requirePending(CandidateFact candidate) {
        if (candidate.getStatus() != CandidateFactStatus.PENDING) {
            throw new BadRequestException("This candidate fact has already been reviewed");
        }
    }

    private CandidateFact findCandidate(UUID id) {
        return candidateFactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate fact not found: " + id));
    }
}
