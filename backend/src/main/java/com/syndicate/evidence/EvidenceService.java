package com.syndicate.evidence;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.candidatefact.CandidateFactStatus;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ChecksumUtil;
import com.syndicate.common.ConflictException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.dto.EvidenceDto;
import com.syndicate.evidence.dto.EvidenceIntegrityDto;
import com.syndicate.ingestion.EvidenceExtractionPublisher;
import com.syndicate.ingestion.PageImageCache;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final EvidenceRepository evidenceRepository;
    private final WorkstreamService workstreamService;
    private final FileStorageService fileStorageService;
    private final EvidenceExtractionPublisher extractionPublisher;
    private final PageImageCache pageImageCache;
    private final CandidateFactRepository candidateFactRepository;
    private final EvidenceFailureRecorder failureRecorder;
    private final com.syndicate.transaction.TransactionService transactionService;
    private final AuditService auditService;

    public EvidenceService(EvidenceRepository evidenceRepository, WorkstreamService workstreamService,
                            FileStorageService fileStorageService, EvidenceExtractionPublisher extractionPublisher,
                            PageImageCache pageImageCache, CandidateFactRepository candidateFactRepository,
                            EvidenceFailureRecorder failureRecorder,
                            com.syndicate.transaction.TransactionService transactionService,
                            AuditService auditService) {
        this.evidenceRepository = evidenceRepository;
        this.workstreamService = workstreamService;
        this.fileStorageService = fileStorageService;
        this.extractionPublisher = extractionPublisher;
        this.pageImageCache = pageImageCache;
        this.candidateFactRepository = candidateFactRepository;
        this.failureRecorder = failureRecorder;
        this.transactionService = transactionService;
        this.auditService = auditService;
    }

    @Transactional
    public EvidenceDto upload(UUID workstreamId, MultipartFile file, EvidenceDocumentType documentType, User caller) {
        workstreamService.requireAccess(workstreamId, caller.getId());
        Workstream workstream = workstreamService.findWorkstream(workstreamId);
        byte[] content = readContent(file);
        String sha256 = ChecksumUtil.sha256Hex(content);
        UUID transactionId = workstream.getTransaction().getId();
        evidenceRepository.findFirstByWorkstreamTransactionIdAndFileSha256AndRetentionStateNot(
                transactionId, sha256, RetentionState.ARCHIVED).ifPresent(existing -> {
            throw new ConflictException("DUPLICATE_EVIDENCE", "This exact file is already on the transaction as \""
                    + existing.getFileName() + "\" (" + existing.getId() + ")");
        });
        Evidence saved = evidenceRepository.save(new Evidence(workstream, originalName(file),
                fileStorageService.store(content, sha256), contentType(file), content.length, documentType,
                caller, sha256));
        auditService.record(transactionId, caller, AuditAction.EVIDENCE_UPLOADED, "Evidence", saved.getId(),
                "Uploaded " + saved.getFileName(), null, sha256, null);
        publishAfterCommit(saved.getId());
        return EvidenceDto.from(saved);
    }

    /**
     * Records a corrected or replacement copy as the next version in the same lineage. The previous
     * version, its file and every fact linked to it are left exactly as they were.
     */
    @Transactional
    public EvidenceDto uploadNewVersion(UUID evidenceId, MultipartFile file, String reason, User caller) {
        Evidence parent = findEvidence(evidenceId);
        workstreamService.requireAccess(parent.getWorkstream().getId(), caller.getId());
        if (parent.getSupersededAt() != null) {
            throw new ConflictException("EVIDENCE_NOT_LATEST", "A newer version of this document already exists");
        }
        if (parent.getRetentionState() != RetentionState.ACTIVE) {
            throw new ConflictException("EVIDENCE_NOT_ACTIVE", "Archived evidence cannot be revised");
        }
        byte[] content = readContent(file);
        String sha256 = ChecksumUtil.sha256Hex(content);
        if (sha256.equals(parent.getFileSha256())) {
            throw new ConflictException("DUPLICATE_EVIDENCE", "The new version is byte-identical to the current one");
        }
        Evidence saved = evidenceRepository.save(Evidence.newVersionOf(parent, originalName(file),
                fileStorageService.store(content, sha256), contentType(file), content.length, caller, sha256));
        parent.markSuperseded(Instant.now());
        candidateFactRepository.findByEvidenceIdAndStatus(parent.getId(), CandidateFactStatus.PENDING)
                .forEach(CandidateFact::supersede);
        auditService.record(parent.getWorkstream().getTransaction().getId(), caller, AuditAction.EVIDENCE_VERSIONED,
                "Evidence", saved.getId(), "New version v" + saved.getVersion() + " of " + parent.getFileName(),
                parent.getFileSha256(), sha256, reason);
        publishAfterCommit(saved.getId());
        return EvidenceDto.from(saved);
    }

    @Transactional
    public EvidenceDto assessQuality(UUID evidenceId, EvidenceQuality quality, String reason, User caller) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), caller.getId());
        if (!EvidenceQuality.REVIEWER_ASSIGNABLE.contains(quality)) {
            throw new BadRequestException("Quality " + quality + " cannot be assigned by a reviewer");
        }
        if (evidence.getSupersededAt() != null) {
            throw new ConflictException("EVIDENCE_NOT_LATEST", "Assess the current version of this document");
        }
        if (quality != EvidenceQuality.ACCEPTABLE && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required when evidence is not acceptable");
        }
        EvidenceQuality previous = evidence.getQuality();
        evidence.assessQuality(quality, reason, caller, Instant.now());
        auditService.record(evidence.getWorkstream().getTransaction().getId(), caller,
                AuditAction.EVIDENCE_QUALITY_ASSESSED, "Evidence", evidence.getId(),
                "Assessed " + evidence.getFileName() + " as " + quality, previous.name(), quality.name(), reason);
        return EvidenceDto.from(evidence);
    }

    public List<EvidenceDto> history(UUID evidenceId, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        return evidenceRepository.findByLineageIdOrderByVersionAsc(evidence.getLineageId()).stream()
                .map(EvidenceDto::from)
                .toList();
    }

    /** Re-hashes the stored original and compares it with the hash recorded at upload. */
    public EvidenceIntegrityDto checkIntegrity(UUID evidenceId, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        String actual = ChecksumUtil.sha256Hex(fileStorageService.readBytes(evidence.getStoragePath()));
        return new EvidenceIntegrityDto(evidence.getId(), evidence.getFileSha256(), actual,
                actual.equals(evidence.getFileSha256()));
    }

    private static byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read uploaded file");
        }
    }

    private static String originalName(MultipartFile file) {
        return file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
    }

    private static String contentType(MultipartFile file) {
        return file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    }

    private void publishAfterCommit(UUID evidenceId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishExtractionJobSafely(evidenceId);
            }
        });
    }

    /** Every document on the deal, so the team has one place to work from. */
    public List<EvidenceDto> listForTransaction(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return evidenceRepository.findByWorkstreamTransactionIdOrderByUploadedAtDesc(transactionId).stream()
                .filter(EvidenceService::isCurrent)
                .map(EvidenceDto::from)
                .toList();
    }

    public List<EvidenceDto> list(UUID workstreamId, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        return evidenceRepository.findByWorkstreamId(workstreamId).stream()
                .filter(EvidenceService::isCurrent)
                .map(EvidenceDto::from)
                .toList();
    }

    /** Lists show the latest active version; older versions and archived documents stay reachable by id. */
    private static boolean isCurrent(Evidence evidence) {
        return evidence.getSupersededAt() == null && evidence.getRetentionState() != RetentionState.ARCHIVED;
    }

    public EvidenceDto get(UUID evidenceId, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        return EvidenceDto.from(evidence);
    }

    public Evidence findEvidence(UUID evidenceId) {
        return evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found: " + evidenceId));
    }

    @Transactional
    public Resource download(UUID evidenceId, User caller) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), caller.getId());
        auditService.record(evidence.getWorkstream().getTransaction().getId(), caller, AuditAction.EVIDENCE_ACCESSED,
                "Evidence", evidence.getId(), "Downloaded " + evidence.getFileName());
        return fileStorageService.load(evidence.getStoragePath());
    }

    /**
     * Withdraws a document without destroying it (spec §53). Evidence that any fact relies on
     * cannot be archived: the fact would silently lose its support.
     */
    @Transactional
    public EvidenceDto archive(UUID evidenceId, String reason, User caller) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), caller.getId());
        if (evidence.getRetentionState() != RetentionState.ACTIVE) {
            throw new ConflictException("EVIDENCE_NOT_ACTIVE", "This evidence is already " + evidence.getRetentionState());
        }
        long references = evidenceRepository.countFactReferences(evidenceId);
        if (references > 0) {
            throw new ConflictException("EVIDENCE_REFERENCED", references
                    + " fact(s) rely on this evidence. Unlink or supersede them before archiving it.");
        }
        evidence.archive(caller, reason, Instant.now());
        auditService.record(evidence.getWorkstream().getTransaction().getId(), caller, AuditAction.EVIDENCE_ARCHIVED,
                "Evidence", evidence.getId(), "Archived " + evidence.getFileName(), null, null, reason);
        return EvidenceDto.from(evidence);
    }

    @Transactional
    public EvidenceDto reprocess(UUID evidenceId, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        if (!isCurrent(evidence)) {
            throw new ConflictException("EVIDENCE_NOT_ACTIVE", "Only the current, active version can be reprocessed");
        }
        evidence.setProcessingStatus(ProcessingStatus.PENDING);
        evidence.setProcessingError(null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishExtractionJobSafely(evidence.getId());
            }
        });
        return EvidenceDto.from(evidence);
    }

    public Resource loadPageImage(UUID evidenceId, int pageNumber, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        return pageImageCache.load(evidenceId, pageNumber);
    }

    /**
     * Publishes the extraction job after the enclosing transaction has committed.
     * If publishing fails (e.g. the message broker is unreachable), the evidence
     * would otherwise be stuck at PENDING forever with no recovery path, since the
     * frontend's Retry action only surfaces for FAILED evidence. Instead, mark the
     * evidence FAILED (in a fresh transaction, since the original has committed)
     * so the existing Retry flow can recover it.
     */
    private void publishExtractionJobSafely(UUID evidenceId) {
        try {
            extractionPublisher.publishExtractionJob(evidenceId);
        } catch (Exception e) {
            log.error("Failed to publish extraction job for evidence {}", evidenceId, e);
            failureRecorder.markExtractionQueueFailure(evidenceId, e);
        }
    }
}
