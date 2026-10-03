package com.syndicate.lifecycle;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.ConflictException;
import com.syndicate.drhp.DisclosureRepository;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.drhp.DrhpStatus;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.filing.FilingPackageRepository;
import com.syndicate.lifecycle.dto.MilestoneDtoMapper;
import com.syndicate.lifecycle.dto.RecordMilestoneRequest;
import com.syndicate.lifecycle.dto.StageDto;
import com.syndicate.observation.ObservationStatus;
import com.syndicate.observation.RegulatoryObservationRepository;
import com.syndicate.permission.Permission;
import com.syndicate.permission.PermissionService;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the transaction stands, worked out from what it holds.
 *
 * <p>Nobody is asked to keep a status in step with reality: documents, verified facts, a compiled
 * document, its approvals and its package already say where things are. Only what happened
 * elsewhere - the filing, the listing - has to be reported, and those are recorded as acts.
 */
@Service
public class LifecycleService {

    private final TransactionMilestoneRepository milestoneRepository;
    private final EvidenceRepository evidenceRepository;
    private final FactRepository factRepository;
    private final DisclosureRepository disclosureRepository;
    private final DrhpDocumentRepository documentRepository;
    private final FilingPackageRepository packageRepository;
    private final RegulatoryObservationRepository observationRepository;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public LifecycleService(TransactionMilestoneRepository milestoneRepository, EvidenceRepository evidenceRepository,
                            FactRepository factRepository, DisclosureRepository disclosureRepository,
                            DrhpDocumentRepository documentRepository, FilingPackageRepository packageRepository,
                            RegulatoryObservationRepository observationRepository,
                            TransactionService transactionService, PermissionService permissionService,
                            AuditService auditService) {
        this.milestoneRepository = milestoneRepository;
        this.evidenceRepository = evidenceRepository;
        this.factRepository = factRepository;
        this.disclosureRepository = disclosureRepository;
        this.documentRepository = documentRepository;
        this.packageRepository = packageRepository;
        this.observationRepository = observationRepository;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public StageDto stage(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return describe(transactionId);
    }

    /** Records that the draft offer document was filed, or that the issue listed. */
    @Transactional
    public StageDto record(UUID transactionId, MilestoneType type, RecordMilestoneRequest request, User caller) {
        permissionService.requireTransactionPermission(transactionId, caller.getId(),
                Permission.TRANSACTION_STATUS_TRANSITION);
        if (milestoneRepository.findByTransactionIdAndType(transactionId, type).isPresent()) {
            throw new ConflictException("MILESTONE_ALREADY_RECORDED", "That has already been recorded");
        }
        DrhpDocument document = null;
        if (type == MilestoneType.FILED) {
            document = documentRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId)
                    .filter(d -> d.getStatus() == DrhpStatus.COMPILED)
                    .orElseThrow(() -> new ConflictException("NOTHING_TO_FILE",
                            "There is no current compiled document to file"));
            if (packageRepository.findByDocumentId(document.getId()).isEmpty()) {
                throw new ConflictException("PACKAGE_NOT_ASSEMBLED",
                        "Assemble the filing package first, so what was filed is on record");
            }
        }
        if (type == MilestoneType.LISTED
                && milestoneRepository.findByTransactionIdAndType(transactionId, MilestoneType.FILED).isEmpty()) {
            throw new ConflictException("NOT_FILED_YET", "Record the filing before the listing");
        }
        milestoneRepository.save(new TransactionMilestone(transactionId, type, request.occurredOn(),
                request.reference(), request.notes(), document, caller));
        auditService.record(transactionId, caller, AuditAction.MILESTONE_RECORDED, "Transaction", transactionId,
                (type == MilestoneType.FILED ? "Recorded the filing on " : "Recorded the listing on ")
                        + request.occurredOn(), null, request.reference(), request.notes());
        return describe(transactionId);
    }

    private StageDto describe(UUID transactionId) {
        Optional<TransactionMilestone> listed =
                milestoneRepository.findByTransactionIdAndType(transactionId, MilestoneType.LISTED);
        Optional<TransactionMilestone> filed =
                milestoneRepository.findByTransactionIdAndType(transactionId, MilestoneType.FILED);
        long openObservations = observationRepository.findByTransactionIdAndStatusIn(transactionId,
                List.of(ObservationStatus.OPEN, ObservationStatus.RESPONSE_DRAFTED,
                        ObservationStatus.RESPONSE_APPROVED)).size();
        Optional<DrhpDocument> latest = documentRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId);
        boolean packaged = latest.map(d -> packageRepository.findByDocumentId(d.getId()).isPresent()).orElse(false);
        boolean compiled = latest.map(d -> d.getStatus() == DrhpStatus.COMPILED).orElse(false);
        boolean hasDisclosures = !disclosureRepository
                .findByTransactionIdOrderByOrderIndexAscCreatedAtAsc(transactionId).isEmpty();
        boolean hasVerifiedFacts = !factRepository
                .findByWorkstreamTransactionIdAndStatus(transactionId, FactStatus.VERIFIED).isEmpty();
        boolean hasEvidence = !evidenceRepository
                .findByWorkstreamTransactionIdOrderByUploadedAtDesc(transactionId).isEmpty();

        TransactionStage stage;
        String summary;
        String nextAction;
        if (listed.isPresent()) {
            stage = TransactionStage.LISTED;
            summary = "Listed on " + listed.get().getOccurredOn() + ".";
            nextAction = null;
        } else if (filed.isPresent() && openObservations > 0) {
            stage = TransactionStage.ANSWERING_OBSERVATIONS;
            summary = openObservations + " observation(s) outstanding since filing on " + filed.get().getOccurredOn() + ".";
            nextAction = "Answer them, with what each answer rests on";
        } else if (filed.isPresent()) {
            stage = TransactionStage.FILED;
            summary = "Filed on " + filed.get().getOccurredOn()
                    + (filed.get().getReference() != null ? " (" + filed.get().getReference() + ")" : "") + ".";
            nextAction = "Record any observation the authority raises";
        } else if (packaged) {
            stage = TransactionStage.FILING_READY;
            summary = "The package for DRHP v" + latest.get().getVersion() + " is assembled.";
            nextAction = "File it, then record the filing here";
        } else if (compiled) {
            stage = TransactionStage.AWAITING_APPROVAL;
            summary = "DRHP v" + latest.get().getVersion() + " is compiled.";
            nextAction = "Collect the required approvals, then assemble the package";
        } else if (hasDisclosures) {
            stage = TransactionStage.PREPARING_THE_DOCUMENT;
            summary = "Disclosures are being written against verified facts.";
            nextAction = "Compile the document when the sections are ready";
        } else if (hasVerifiedFacts) {
            stage = TransactionStage.DILIGENCE;
            summary = "Facts are being established and verified.";
            nextAction = "Answer the diligence questions and start the disclosures";
        } else {
            stage = TransactionStage.COLLECTING_DOCUMENTS;
            summary = hasEvidence ? "Documents are in; nothing has been confirmed yet."
                    : "Nothing has been collected yet.";
            nextAction = hasEvidence ? "Confirm what the documents establish" : "Upload the company's documents";
        }

        List<MilestoneType> recordable = new ArrayList<>();
        if (filed.isEmpty() && packaged) {
            recordable.add(MilestoneType.FILED);
        }
        if (filed.isPresent() && listed.isEmpty()) {
            recordable.add(MilestoneType.LISTED);
        }
        return new StageDto(stage, summary, nextAction,
                milestoneRepository.findByTransactionIdOrderByOccurredOnAsc(transactionId).stream()
                        .map(MilestoneDtoMapper::toDto).toList(),
                recordable);
    }
}
