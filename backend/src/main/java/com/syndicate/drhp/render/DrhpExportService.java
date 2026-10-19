package com.syndicate.drhp.render;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.common.ChecksumUtil;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.drhp.dto.CompiledSection;
import com.syndicate.drhp.dto.CompiledSegment;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.filing.DocumentApproval;
import com.syndicate.filing.DocumentApprovalRepository;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DrhpExportService {

    private final DrhpDocumentRepository documentRepository;
    private final DocumentApprovalRepository approvalRepository;
    private final TransactionService transactionService;
    private final AuditService auditService;
    private final PdfDocumentRenderer pdfRenderer;
    private final DocxDocumentRenderer docxRenderer;
    private final ObjectMapper objectMapper;

    public DrhpExportService(DrhpDocumentRepository documentRepository,
                             DocumentApprovalRepository approvalRepository,
                             TransactionService transactionService,
                             AuditService auditService,
                             PdfDocumentRenderer pdfRenderer,
                             DocxDocumentRenderer docxRenderer,
                             ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.approvalRepository = approvalRepository;
        this.transactionService = transactionService;
        this.auditService = auditService;
        this.pdfRenderer = pdfRenderer;
        this.docxRenderer = docxRenderer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public byte[] exportPdf(UUID documentId, User caller) {
        DrhpDocument doc = findDocument(documentId);
        transactionService.requireMembership(doc.getTransaction().getId(), caller.getId());
        byte[] bytes = renderPdfForDocument(doc);
        recordExportAudit(doc, caller, "PDF", bytes);
        return bytes;
    }

    @Transactional
    public byte[] exportDocx(UUID documentId, User caller) {
        DrhpDocument doc = findDocument(documentId);
        transactionService.requireMembership(doc.getTransaction().getId(), caller.getId());
        byte[] bytes = renderDocxForDocument(doc);
        recordExportAudit(doc, caller, "DOCX", bytes);
        return bytes;
    }

    @Transactional
    public byte[] exportLatestPdf(UUID transactionId, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        DrhpDocument doc = documentRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("No compiled DRHP found for this transaction"));
        return exportPdf(doc.getId(), caller);
    }

    @Transactional
    public byte[] exportLatestDocx(UUID transactionId, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        DrhpDocument doc = documentRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("No compiled DRHP found for this transaction"));
        return exportDocx(doc.getId(), caller);
    }

    public byte[] renderPdfForDocument(DrhpDocument document) {
        DocumentRenderModel model = buildRenderModel(document);
        return pdfRenderer.render(model);
    }

    public byte[] renderDocxForDocument(DrhpDocument document) {
        DocumentRenderModel model = buildRenderModel(document);
        return docxRenderer.render(model);
    }

    public DocumentRenderModel buildRenderModel(DrhpDocument document) {
        List<CompiledSection> rawSections = readSections(document.getCompiledBody());

        // Map facts cited in segments to 1-based footnote indices
        Map<UUID, Integer> factToFootnoteIndex = new LinkedHashMap<>();
        Map<String, Integer> keyToFootnoteIndex = new HashMap<>();
        List<DocumentRenderModel.RenderFactFootnote> footnotes = new ArrayList<>();

        Map<UUID, Fact> citedFactsById = new HashMap<>();
        Map<String, Fact> citedFactsByKey = new HashMap<>();
        for (Fact f : document.getCitedFacts()) {
            if (f.getId() != null) {
                citedFactsById.put(f.getId(), f);
            }
            if (f.getFactKey() != null) {
                citedFactsByKey.put(f.getFactKey().toLowerCase(), f);
            }
        }

        List<DocumentRenderModel.RenderSection> renderSections = new ArrayList<>();
        for (CompiledSection sec : rawSections) {
            List<DocumentRenderModel.SectionSegment> renderSegments = new ArrayList<>();
            StringBuilder prose = new StringBuilder();

            if (sec.segments() != null) {
                for (CompiledSegment seg : sec.segments()) {
                    if ("FACT".equalsIgnoreCase(seg.type())) {
                        Fact match = seg.factId() != null ? citedFactsById.get(seg.factId()) : null;
                        if (match == null && seg.label() != null) {
                            match = citedFactsByKey.get(seg.label().toLowerCase());
                        }

                        Integer footnoteIdx = null;
                        if (match != null) {
                            if (!factToFootnoteIndex.containsKey(match.getId())) {
                                int newIdx = footnotes.size() + 1;
                                factToFootnoteIndex.put(match.getId(), newIdx);
                                keyToFootnoteIndex.put(match.getFactKey().toLowerCase(), newIdx);

                                List<DocumentRenderModel.EvidenceSummary> evSummaries = new ArrayList<>();
                                if (match.getEvidence() != null) {
                                    for (Evidence ev : match.getEvidence()) {
                                        evSummaries.add(new DocumentRenderModel.EvidenceSummary(
                                                ev.getId(), ev.getFileName(), ev.getDocumentType().name(), ev.getFileSha256()));
                                    }
                                }

                                footnotes.add(new DocumentRenderModel.RenderFactFootnote(
                                        newIdx,
                                        match.getId(),
                                        match.getFactKey(),
                                        match.getLabel(),
                                        seg.value() != null ? seg.value() : match.getValue(),
                                        seg.unit() != null ? seg.unit() : match.getUnit(),
                                        match.getPeriod(),
                                        match.getVerifiedByUser() != null ? match.getVerifiedByUser().getFullName() : null,
                                        match.getVerifiedAt(),
                                        evSummaries
                                ));
                            }
                            footnoteIdx = factToFootnoteIndex.get(match.getId());
                        }

                        renderSegments.add(new DocumentRenderModel.SectionSegment(
                                "FACT",
                                null,
                                footnoteIdx,
                                seg.value(),
                                seg.label()
                        ));
                        prose.append(seg.value() != null ? seg.value() : seg.label());
                        if (footnoteIdx != null) {
                            prose.append(" [").append(footnoteIdx).append("]");
                        }
                    } else if ("UNRESOLVED".equalsIgnoreCase(seg.type())) {
                        renderSegments.add(new DocumentRenderModel.SectionSegment(
                                "UNRESOLVED",
                                null,
                                null,
                                null,
                                seg.label()
                        ));
                        prose.append("[UNRESOLVED: ").append(seg.label()).append("]");
                    } else {
                        renderSegments.add(new DocumentRenderModel.SectionSegment(
                                "TEXT",
                                seg.text(),
                                null,
                                null,
                                null
                        ));
                        prose.append(seg.text() != null ? seg.text() : "");
                    }
                }
            }

            renderSections.add(new DocumentRenderModel.RenderSection(
                    sec.disclosureId(),
                    sec.sectionCode(),
                    sec.title(),
                    prose.toString(),
                    renderSegments
            ));
        }

        // Approvals
        List<DocumentApproval> approvals = approvalRepository.findByDocumentId(document.getId());
        List<DocumentRenderModel.RenderApproval> renderApprovals = approvals.stream()
                .map(a -> new DocumentRenderModel.RenderApproval(
                        a.getApprover() != null ? a.getApprover().getFullName() : "Unknown",
                        a.getApproverRole() != null ? a.getApproverRole().name() : "MEMBER",
                        a.getGrantedAt(),
                        a.isCurrent(),
                        a.getInvalidationReason()
                ))
                .toList();

        return new DocumentRenderModel(
                document.getId(),
                document.getTransaction().getId(),
                document.getTransaction().getCompany() != null ? document.getTransaction().getCompany().getLegalName() : "Issuer Company",
                document.getTransaction().getName() != null ? document.getTransaction().getName() : "Public Issue",
                document.getVersion(),
                document.getStatus(),
                document.getCompileMode(),
                document.getMerkleRoot(),
                document.getCompiledAt(),
                document.getCompiledByUser() != null ? document.getCompiledByUser().getFullName() : "System",
                document.getLintSummary(),
                renderSections,
                footnotes,
                renderApprovals
        );
    }

    private void recordExportAudit(DrhpDocument doc, User caller, String format, byte[] bytes) {
        String sha256 = ChecksumUtil.sha256Hex(bytes);
        String summary = "Exported DRHP v" + doc.getVersion() + " as " + format + " (" + bytes.length + " bytes)";
        auditService.record(doc.getTransaction().getId(), caller, AuditAction.DRHP_EXPORTED,
                "DrhpDocument", doc.getId(), summary, null, sha256, format);
    }

    private DrhpDocument findDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("DRHP Document not found: " + documentId));
    }

    private List<CompiledSection> readSections(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CompiledSection>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not read compiled DRHP sections: " + e.getMessage(), e);
        }
    }
}
