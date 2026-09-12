package com.syndicate.candidatefact;

import com.syndicate.candidatefact.dto.AcceptCandidateFactRequest;
import com.syndicate.candidatefact.dto.CandidateFactDto;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.user.User;
import com.syndicate.workstream.WorkstreamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CandidateFactService {

    private final CandidateFactRepository candidateFactRepository;
    private final FactRepository factRepository;
    private final WorkstreamService workstreamService;

    public CandidateFactService(CandidateFactRepository candidateFactRepository, FactRepository factRepository,
                                 WorkstreamService workstreamService) {
        this.candidateFactRepository = candidateFactRepository;
        this.factRepository = factRepository;
        this.workstreamService = workstreamService;
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
