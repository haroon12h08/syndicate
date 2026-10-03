package com.syndicate.observation;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ConflictException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.drhp.Disclosure;
import com.syndicate.drhp.DisclosureRepository;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.FactRepository;
import com.syndicate.observation.dto.ObservationDto;
import com.syndicate.observation.dto.RecordObservationRequest;
import com.syndicate.observation.dto.RespondRequest;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Queries from an exchange or regulator, kept as part of the transaction (spec §18).
 *
 * <p>The point is not correspondence tracking. It is that a response is tied to the facts,
 * documents and disclosures it rests on, and to the compiled version it was given against, so a
 * year later it is clear what was said and what was true when it was said.
 */
@Service
@Transactional
public class ObservationService {

    private final RegulatoryObservationRepository observationRepository;
    private final FactRepository factRepository;
    private final EvidenceRepository evidenceRepository;
    private final DisclosureRepository disclosureRepository;
    private final DrhpDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public ObservationService(RegulatoryObservationRepository observationRepository, FactRepository factRepository,
                              EvidenceRepository evidenceRepository, DisclosureRepository disclosureRepository,
                              DrhpDocumentRepository documentRepository, UserRepository userRepository,
                              TransactionService transactionService, PermissionService permissionService,
                              AuditService auditService) {
        this.observationRepository = observationRepository;
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.disclosureRepository = disclosureRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ObservationDto> list(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return observationRepository.findByTransactionIdOrderByReceivedDateDesc(transactionId).stream()
                .map(ObservationDto::from)
                .toList();
    }

    public ObservationDto record(UUID transactionId, RecordObservationRequest request, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        User owner = request.ownerUserId() == null ? caller : userRepository.findById(request.ownerUserId())
                .orElseThrow(() -> new BadRequestException("Unknown owner"));
        RegulatoryObservation observation = observationRepository.save(new RegulatoryObservation(transactionId,
                request.authority(), request.reference(), request.receivedDate(), request.observation(),
                request.sectionCode(), request.severity(), request.responseDeadline(), owner));
        link(observation, request.factIds(), request.evidenceIds(), request.disclosureIds());
        auditService.record(transactionId, caller, AuditAction.OBSERVATION_RECORDED, "RegulatoryObservation",
                observation.getId(), request.authority() + " raised: " + request.observation(), null, null, null);
        return ObservationDto.from(observation);
    }

    public ObservationDto respond(UUID observationId, RespondRequest request, User caller) {
        RegulatoryObservation observation = find(observationId);
        transactionService.requireMembership(observation.getTransactionId(), caller.getId());
        if (observation.getStatus() == ObservationStatus.CLOSED) {
            throw new ConflictException("OBSERVATION_CLOSED", "This observation is closed");
        }
        link(observation, request.factIds(), request.evidenceIds(), request.disclosureIds());
        if (observation.getRelatedFacts().isEmpty() && observation.getRelatedEvidence().isEmpty()
                && observation.getRelatedDisclosures().isEmpty()) {
            throw new BadRequestException("Point the response at the facts, documents or disclosures it rests on");
        }
        observation.draftResponse(request.response(), caller);
        auditService.record(observation.getTransactionId(), caller, AuditAction.OBSERVATION_RESPONSE_DRAFTED,
                "RegulatoryObservation", observation.getId(), "Drafted a response", null, null, null);
        return ObservationDto.from(observation);
    }

    /** Approving freezes which compiled version the answer was given against. */
    public ObservationDto approveResponse(UUID observationId, User caller) {
        RegulatoryObservation observation = find(observationId);
        UUID transactionId = observation.getTransactionId();
        permissionService.requireTransactionPermission(transactionId, caller.getId(), Permission.CONFLICT_RESOLVE);
        if (observation.getStatus() != ObservationStatus.RESPONSE_DRAFTED) {
            throw new ConflictException("NO_RESPONSE_TO_APPROVE",
                    "There is no drafted response to approve (this observation is " + observation.getStatus() + ")");
        }
        if (observation.getRespondedBy() != null && observation.getRespondedBy().getId().equals(caller.getId())) {
            throw new ConflictException("RESPONSE_NOT_INDEPENDENT",
                    "You wrote this response, so someone else has to approve it");
        }
        Optional<DrhpDocument> latest = documentRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId);
        observation.approveResponse(caller, latest.orElse(null));
        auditService.record(transactionId, caller, AuditAction.OBSERVATION_RESPONSE_APPROVED,
                "RegulatoryObservation", observation.getId(), "Approved the response", null,
                latest.map(d -> "DRHP v" + d.getVersion()).orElse(null), null);
        return ObservationDto.from(observation);
    }

    public ObservationDto markSent(UUID observationId, User caller) {
        RegulatoryObservation observation = find(observationId);
        permissionService.requireTransactionPermission(observation.getTransactionId(), caller.getId(),
                Permission.CONFLICT_RESOLVE);
        if (observation.getStatus() != ObservationStatus.RESPONSE_APPROVED) {
            throw new ConflictException("RESPONSE_NOT_APPROVED", "Have the response approved before sending it");
        }
        observation.markSent();
        auditService.record(observation.getTransactionId(), caller, AuditAction.OBSERVATION_RESPONDED,
                "RegulatoryObservation", observation.getId(), "Recorded the response as sent", null, null, null);
        return ObservationDto.from(observation);
    }

    public ObservationDto close(UUID observationId, User caller) {
        RegulatoryObservation observation = find(observationId);
        permissionService.requireTransactionPermission(observation.getTransactionId(), caller.getId(),
                Permission.CONFLICT_RESOLVE);
        observation.close();
        auditService.record(observation.getTransactionId(), caller, AuditAction.OBSERVATION_CLOSED,
                "RegulatoryObservation", observation.getId(), "Closed the observation", null, null, null);
        return ObservationDto.from(observation);
    }

    private void link(RegulatoryObservation observation, List<UUID> factIds, List<UUID> evidenceIds,
                      List<UUID> disclosureIds) {
        UUID transactionId = observation.getTransactionId();
        if (factIds != null) {
            factIds.forEach(id -> observation.getRelatedFacts().add(factRepository.findById(id)
                    .filter(f -> f.getWorkstream().getTransaction().getId().equals(transactionId))
                    .orElseThrow(() -> new BadRequestException("Unknown fact " + id))));
        }
        if (evidenceIds != null) {
            evidenceIds.forEach(id -> observation.getRelatedEvidence().add(evidenceRepository.findById(id)
                    .filter(e -> e.getWorkstream().getTransaction().getId().equals(transactionId))
                    .orElseThrow(() -> new BadRequestException("Unknown document " + id))));
        }
        if (disclosureIds != null) {
            for (UUID id : disclosureIds) {
                Disclosure disclosure = disclosureRepository.findById(id)
                        .filter(d -> d.getTransaction().getId().equals(transactionId))
                        .orElseThrow(() -> new BadRequestException("Unknown disclosure " + id));
                observation.getRelatedDisclosures().add(disclosure);
            }
        }
    }

    private RegulatoryObservation find(UUID observationId) {
        return observationRepository.findById(observationId)
                .orElseThrow(() -> new ResourceNotFoundException("Observation not found: " + observationId));
    }
}
