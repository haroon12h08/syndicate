package com.syndicate.filing;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.ChecksumUtil;
import com.syndicate.common.ConflictException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.conflict.ConflictStatus;
import com.syndicate.conflict.FactConflictRepository;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.drhp.DrhpStatus;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.FileStorageService;
import com.syndicate.fact.Fact;
import com.syndicate.filing.dto.ApprovalDto;
import com.syndicate.filing.dto.FilingStatusDto;
import com.syndicate.provenance.dto.ProvenanceManifestDto;
import com.syndicate.provenance.ProvenanceService;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.transaction.TransactionService;
import com.syndicate.permission.PermissionService;
import com.syndicate.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Approval of a compiled document, and assembly of the package that is handed to whoever files.
 *
 * <p>Syndicate does not file anything (spec §36). It produces a package and the proof of what
 * state produced it; the act of filing stays with the people who are responsible for it.
 */
@Service
public class FilingService {

    private final DrhpDocumentRepository documentRepository;
    private final DocumentApprovalRepository approvalRepository;
    private final FilingPackageRepository packageRepository;
    private final FactConflictRepository conflictRepository;
    private final ProvenanceService provenanceService;
    private final TransactionService transactionService;
    private final PermissionService permissionService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    /** The application's mapper, so dates serialise the same way here as everywhere else. */
    private final ObjectMapper objectMapper;

    public FilingService(DrhpDocumentRepository documentRepository, DocumentApprovalRepository approvalRepository,
                         FilingPackageRepository packageRepository, FactConflictRepository conflictRepository,
                         ProvenanceService provenanceService, TransactionService transactionService,
                         PermissionService permissionService, FileStorageService fileStorageService,
                         AuditService auditService, ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.approvalRepository = approvalRepository;
        this.packageRepository = packageRepository;
        this.conflictRepository = conflictRepository;
        this.provenanceService = provenanceService;
        this.transactionService = transactionService;
        this.permissionService = permissionService;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** Approves one exact compiled version, in the caller's transaction role. */
    @Transactional
    public ApprovalDto approve(UUID documentId, User caller) {
        DrhpDocument document = findDocument(documentId);
        UUID transactionId = document.getTransaction().getId();
        TransactionRole role = permissionService.requireTransactionMembershipRole(transactionId, caller.getId());
        if (!approvalRepository.requiredRoles().contains(role.name())) {
            throw new ConflictException("APPROVAL_ROLE_NOT_REQUIRED",
                    "Approval is given by " + String.join(" and ", approvalRepository.requiredRoles())
                            + "; your role on this transaction is " + role);
        }
        if (document.getStatus() == DrhpStatus.INVALIDATED) {
            throw new ConflictException("DOCUMENT_INVALIDATED",
                    "This version no longer matches the transaction. Recompile and approve the new version.");
        }
        for (String blocker : blockers(document)) {
            throw new ConflictException("APPROVAL_BLOCKED", blocker);
        }
        Optional<DocumentApproval> existing = approvalRepository.findByDocumentIdAndApproverRole(documentId, role);
        if (existing.isPresent() && existing.get().isCurrent()) {
            throw new ConflictException("ALREADY_APPROVED", "This version is already approved as " + role);
        }
        DocumentApproval approval = approvalRepository.save(
                new DocumentApproval(transactionId, document, caller, role));
        auditService.record(transactionId, caller, AuditAction.DOCUMENT_APPROVED, "DrhpDocument", documentId,
                "Approved DRHP v" + document.getVersion() + " as " + role, null, role.name(), null);
        return ApprovalDto.from(approval);
    }

    /** Called when a document stops matching the transaction: its approvals stop meaning anything. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateApprovals(UUID documentId, String reason) {
        for (DocumentApproval approval : approvalRepository.findByDocumentId(documentId)) {
            approval.invalidate(reason);
        }
    }

    @Transactional(readOnly = true)
    public FilingStatusDto status(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        Optional<DrhpDocument> latest = documentRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId);
        if (latest.isEmpty()) {
            return new FilingStatusDto(null, null, null, List.of(), requiredRoles(),
                    List.of("No document has been compiled yet."), false, null);
        }
        DrhpDocument document = latest.get();
        List<DocumentApproval> approvals = approvalRepository.findByDocumentId(document.getId());
        Set<TransactionRole> approvedBy = new LinkedHashSet<>();
        approvals.stream().filter(DocumentApproval::isCurrent).forEach(a -> approvedBy.add(a.getApproverRole()));
        List<TransactionRole> awaiting = requiredRoles().stream().filter(r -> !approvedBy.contains(r)).toList();

        List<String> blockers = new ArrayList<>(blockers(document));
        if (document.getStatus() == DrhpStatus.INVALIDATED) {
            blockers.add("DRHP v" + document.getVersion() + " no longer matches the transaction: "
                    + document.getInvalidatedReason());
        }
        if (!awaiting.isEmpty()) {
            blockers.add("Awaiting approval from " + join(awaiting));
        }
        Optional<FilingPackage> built = packageRepository.findByDocumentId(document.getId());
        return new FilingStatusDto(document.getId(), document.getVersion(), document.getStatus().name(),
                approvals.stream().map(ApprovalDto::from).toList(), awaiting, blockers, blockers.isEmpty(),
                built.map(FilingService::toPackageDto).orElse(null));
    }

    /**
     * Assembles the package: the compiled document, an index of every piece of evidence behind it,
     * the provenance manifest and a certificate naming who approved what.
     */
    @Transactional
    public FilingStatusDto assemble(UUID documentId, User caller) {
        DrhpDocument document = findDocument(documentId);
        UUID transactionId = document.getTransaction().getId();
        transactionService.requireMembership(transactionId, caller.getId());
        if (document.getStatus() == DrhpStatus.INVALIDATED) {
            throw new ConflictException("DOCUMENT_INVALIDATED",
                    "This version no longer matches the transaction. Recompile before assembling a package.");
        }
        List<String> blockers = blockers(document);
        if (!blockers.isEmpty()) {
            throw new ConflictException("FILING_BLOCKED", blockers.get(0));
        }
        Set<TransactionRole> approvedBy = new LinkedHashSet<>();
        approvalRepository.findByDocumentId(documentId).stream().filter(DocumentApproval::isCurrent)
                .forEach(a -> approvedBy.add(a.getApproverRole()));
        List<TransactionRole> awaiting = requiredRoles().stream().filter(r -> !approvedBy.contains(r)).toList();
        if (!awaiting.isEmpty()) {
            throw new ConflictException("APPROVAL_MISSING", "Still awaiting approval from " + join(awaiting));
        }
        if (packageRepository.findByDocumentId(documentId).isPresent()) {
            throw new ConflictException("PACKAGE_EXISTS", "This version has already been assembled");
        }

        ProvenanceManifestDto manifest = provenanceService.get(documentId, caller.getId());
        byte[] archive = buildArchive(document, manifest);
        String sha256 = ChecksumUtil.sha256Hex(archive);
        String fileName = "syndicate-filing-package-v" + document.getVersion() + ".zip";
        String storagePath = fileStorageService.store(archive, sha256);
        packageRepository.save(new FilingPackage(transactionId, document, storagePath, fileName, archive.length,
                sha256, manifest.merkleRoot(), caller));
        auditService.record(transactionId, caller, AuditAction.FILING_PACKAGE_ASSEMBLED, "DrhpDocument", documentId,
                "Assembled filing package for DRHP v" + document.getVersion(), null, sha256, null);
        return status(transactionId, caller.getId());
    }

    @Transactional(readOnly = true)
    public FilingPackage packageFor(UUID documentId, UUID callerId) {
        FilingPackage filingPackage = packageRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("No filing package has been assembled yet"));
        transactionService.requireMembership(filingPackage.getTransactionId(), callerId);
        return filingPackage;
    }

    public Resource load(FilingPackage filingPackage) {
        return fileStorageService.load(filingPackage.getStoragePath());
    }

    /** Reasons the document cannot be approved or packaged, in the words a reviewer would use. */
    private List<String> blockers(DrhpDocument document) {
        List<String> blockers = new ArrayList<>();
        long openConflicts = conflictRepository
                .findByTransactionIdAndStatus(document.getTransaction().getId(), ConflictStatus.OPEN).size();
        if (openConflicts > 0) {
            blockers.add(openConflicts + " conflicting value(s) are still unresolved.");
        }
        return blockers;
    }

    private List<TransactionRole> requiredRoles() {
        return approvalRepository.requiredRoles().stream().map(TransactionRole::valueOf).toList();
    }

    private static String join(List<TransactionRole> roles) {
        return String.join(" and ", roles.stream().map(Enum::name).toList());
    }

    private DrhpDocument findDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private static FilingStatusDto.PackageDto toPackageDto(FilingPackage p) {
        return new FilingStatusDto.PackageDto(p.getId(), p.getFileName(), p.getSizeBytes(), p.getSha256(),
                p.getMerkleRoot(), p.getBuiltAt(), p.getBuiltBy().getFullName());
    }

    private byte[] buildArchive(DrhpDocument document, ProvenanceManifestDto manifest) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            write(zip, "document.json", document.getCompiledBody());
            write(zip, "provenance-manifest.json", objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(manifest));
            write(zip, "evidence-index.csv", evidenceIndex(document));
            write(zip, "approval-certificate.txt", approvalCertificate(document, manifest));
            write(zip, "README.txt", """
                    Syndicate filing package
                    ========================
                    document.json             the compiled document, section by section, with every value
                                              traced to the fact and evidence it came from
                    evidence-index.csv        every document relied upon, with its SHA-256
                    provenance-manifest.json  the Merkle manifest of the exact state this was built from
                    approval-certificate.txt  who approved this version, and when

                    This package was produced by Syndicate. It is not a filing, and nothing in it is a
                    statement that any regulator has approved this issue.
                    """);
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not assemble the filing package: " + e.getMessage(), e);
        }
    }

    private String evidenceIndex(DrhpDocument document) {
        StringBuilder csv = new StringBuilder("evidence_id,file_name,document_type,sha256,uploaded_by,uploaded_at,"
                + "quality,supports_fact\n");
        for (Fact fact : document.getCitedFacts()) {
            for (Evidence evidence : fact.getEvidence()) {
                csv.append(String.join(",", evidence.getId().toString(), quote(evidence.getFileName()),
                        evidence.getDocumentType().name(), String.valueOf(evidence.getFileSha256()),
                        quote(evidence.getUploadedByUser().getFullName()), String.valueOf(evidence.getUploadedAt()),
                        evidence.getQuality().name(), quote(fact.getLabel()))).append('\n');
            }
        }
        return csv.toString();
    }

    private String approvalCertificate(DrhpDocument document, ProvenanceManifestDto manifest) {
        StringBuilder text = new StringBuilder();
        text.append("Approval certificate\n====================\n\n");
        text.append("Transaction: ").append(document.getTransaction().getName()).append('\n');
        text.append("Document:    DRHP version ").append(document.getVersion()).append('\n');
        text.append("Compiled:    ").append(document.getCompiledAt()).append('\n');
        text.append("Merkle root: ").append(manifest.merkleRoot()).append('\n');
        text.append("Assembled:   ").append(Instant.now()).append("\n\nApprovals\n---------\n");
        for (DocumentApproval approval : approvalRepository.findByDocumentId(document.getId())) {
            text.append(approval.getApproverRole()).append(": ").append(approval.getApprover().getFullName())
                    .append(" (").append(approval.getApprover().getEmail()).append(") at ")
                    .append(approval.getGrantedAt());
            if (!approval.isCurrent()) {
                text.append("  [INVALIDATED: ").append(approval.getInvalidationReason()).append(']');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private static void write(ZipOutputStream zip, String name, String content) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
