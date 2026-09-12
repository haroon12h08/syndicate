package com.syndicate.fact;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ForbiddenException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.dto.CreateFactRequest;
import com.syndicate.fact.dto.FactDto;
import com.syndicate.fact.dto.UpdateFactRequest;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamService;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FactService {

    private final FactRepository factRepository;
    private final EvidenceRepository evidenceRepository;
    private final WorkstreamService workstreamService;
    private final PermissionService permissionService;

    public FactService(FactRepository factRepository, EvidenceRepository evidenceRepository,
                        WorkstreamService workstreamService, PermissionService permissionService) {
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.workstreamService = workstreamService;
        this.permissionService = permissionService;
    }

    @Transactional
    public FactDto create(UUID workstreamId, CreateFactRequest request, User caller) {
        workstreamService.requireAccess(workstreamId, caller.getId());
        Workstream workstream = workstreamService.findWorkstream(workstreamId);
        Fact fact = new Fact(workstream, request.label(), request.value(), request.unit(), request.period(),
                null, 1, caller);
        return FactDto.from(factRepository.save(fact));
    }

    public List<FactDto> list(UUID workstreamId, boolean includeSuperseded, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        List<Fact> facts = includeSuperseded
                ? factRepository.findByWorkstreamId(workstreamId)
                : factRepository.findByWorkstreamIdAndStatusNot(workstreamId, FactStatus.SUPERSEDED);
        return facts.stream().map(FactDto::from).toList();
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
        oldFact.setStatus(FactStatus.SUPERSEDED);
        Fact newFact = new Fact(oldFact.getWorkstream(), request.label(), request.value(), request.unit(),
                request.period(), oldFact, oldFact.getVersion() + 1, caller);
        return FactDto.from(factRepository.save(newFact));
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
        return FactDto.from(fact);
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
