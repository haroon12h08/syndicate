package com.syndicate.evidence;

import com.syndicate.common.BadRequestException;
import com.syndicate.common.ChecksumUtil;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.dto.EvidenceDto;
import com.syndicate.ingestion.EvidenceExtractionPublisher;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    private final WorkstreamService workstreamService;
    private final FileStorageService fileStorageService;
    private final EvidenceExtractionPublisher extractionPublisher;

    public EvidenceService(EvidenceRepository evidenceRepository, WorkstreamService workstreamService,
                            FileStorageService fileStorageService, EvidenceExtractionPublisher extractionPublisher) {
        this.evidenceRepository = evidenceRepository;
        this.workstreamService = workstreamService;
        this.fileStorageService = fileStorageService;
        this.extractionPublisher = extractionPublisher;
    }

    @Transactional
    public EvidenceDto upload(UUID workstreamId, MultipartFile file, EvidenceDocumentType documentType, User caller) {
        workstreamService.requireAccess(workstreamId, caller.getId());
        Workstream workstream = workstreamService.findWorkstream(workstreamId);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read uploaded file");
        }
        String originalFilename = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String storedName = fileStorageService.store(content, originalFilename);
        String sha256 = ChecksumUtil.sha256Hex(content);
        Evidence evidence = new Evidence(
                workstream,
                originalFilename,
                storedName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(),
                documentType,
                caller,
                sha256
        );
        Evidence saved = evidenceRepository.save(evidence);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                extractionPublisher.publishExtractionJob(saved.getId());
            }
        });
        return EvidenceDto.from(saved);
    }

    public List<EvidenceDto> list(UUID workstreamId, UUID callerId) {
        workstreamService.requireAccess(workstreamId, callerId);
        return evidenceRepository.findByWorkstreamId(workstreamId).stream().map(EvidenceDto::from).toList();
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

    public Resource download(UUID evidenceId, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        return fileStorageService.load(evidence.getStoragePath());
    }

    @Transactional
    public void delete(UUID evidenceId, UUID callerId) {
        Evidence evidence = findEvidence(evidenceId);
        workstreamService.requireAccess(evidence.getWorkstream().getId(), callerId);
        fileStorageService.delete(evidence.getStoragePath());
        evidenceRepository.delete(evidence);
    }
}
