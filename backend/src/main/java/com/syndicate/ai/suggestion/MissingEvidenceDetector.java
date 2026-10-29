package com.syndicate.ai.suggestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceDocumentType;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.transaction.Transaction;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import com.syndicate.workstream.WorkstreamType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MissingEvidenceDetector {

    private final EvidenceRepository evidenceRepository;
    private final WorkstreamRepository workstreamRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MissingEvidenceDetector(EvidenceRepository evidenceRepository,
                                  WorkstreamRepository workstreamRepository) {
        this.evidenceRepository = evidenceRepository;
        this.workstreamRepository = workstreamRepository;
    }

    public List<AiSuggestion> detect(Transaction transaction) {
        List<Evidence> evidenceList = evidenceRepository
                .findByWorkstreamTransactionIdOrderByUploadedAtDesc(transaction.getId());

        Set<EvidenceDocumentType> presentTypes = new HashSet<>();
        for (Evidence e : evidenceList) {
            if (e.getDocumentType() != null) {
                presentTypes.add(e.getDocumentType());
            }
        }

        List<Workstream> workstreams = workstreamRepository.findByTransactionId(transaction.getId());
        Map<WorkstreamType, Workstream> wsByType = new HashMap<>();
        for (Workstream ws : workstreams) {
            wsByType.put(ws.getType(), ws);
        }

        List<AiSuggestion> suggestions = new ArrayList<>();

        // 1. Audited Financial Statements
        if (!presentTypes.contains(EvidenceDocumentType.AUDITED_FINANCIAL_STATEMENT)) {
            Workstream ws = wsByType.getOrDefault(WorkstreamType.FINANCIAL_DUE_DILIGENCE, workstreams.isEmpty() ? null : workstreams.get(0));
            suggestions.add(createSuggestion(
                    transaction, ws,
                    "Missing Mandatory Evidence: Audited Financial Statements",
                    "BSE SME IPO guidance mandates 3 preceding financial years of audited balance sheets and P&L statements. No audited financial statements are currently uploaded.",
                    SuggestionSeverity.BLOCKING,
                    "AUDITED_FINANCIAL_STATEMENT"
            ));
        }

        // 2. Board Resolution
        if (!presentTypes.contains(EvidenceDocumentType.BOARD_RESOLUTION)) {
            Workstream ws = wsByType.getOrDefault(WorkstreamType.CORPORATE_SECRETARIAL, workstreams.isEmpty() ? null : workstreams.get(0));
            suggestions.add(createSuggestion(
                    transaction, ws,
                    "Missing Mandatory Evidence: Board Resolution for IPO",
                    "SEBI ICDR Regulations require the Issuer Board Resolution authorizing the initial public offering and DRHP filing. Upload the certified board meeting extract.",
                    SuggestionSeverity.BLOCKING,
                    "BOARD_RESOLUTION"
            ));
        }

        // 3. Statutory Auditor Peer Review Certificate
        if (!presentTypes.contains(EvidenceDocumentType.CERTIFICATE)) {
            Workstream ws = wsByType.getOrDefault(WorkstreamType.FINANCIAL_DUE_DILIGENCE, workstreams.isEmpty() ? null : workstreams.get(0));
            suggestions.add(createSuggestion(
                    transaction, ws,
                    "Missing Recommended Evidence: Auditor Peer Review Certificate",
                    "Regulation requires that financial statements included in the DRHP be audited by a peer-reviewed statutory auditor holding a valid ICAI Peer Review Certificate.",
                    SuggestionSeverity.WARNING,
                    "CERTIFICATE"
            ));
        }

        // 4. Material Contracts
        if (!presentTypes.contains(EvidenceDocumentType.CONTRACT)) {
            Workstream ws = wsByType.getOrDefault(WorkstreamType.MATERIAL_CONTRACTS, workstreams.isEmpty() ? null : workstreams.get(0));
            suggestions.add(createSuggestion(
                    transaction, ws,
                    "Missing Evidence: Material Commercial Contracts",
                    "Disclosures in 'Material Contracts and Documents' section require executed customer/supplier agreements accounting for >10% of revenue or supply.",
                    SuggestionSeverity.WARNING,
                    "CONTRACT"
            ));
        }

        // 5. Property Documents
        if (!presentTypes.contains(EvidenceDocumentType.PROPERTY_DOCUMENT)) {
            Workstream ws = wsByType.getOrDefault(WorkstreamType.LEGAL_DUE_DILIGENCE, workstreams.isEmpty() ? null : workstreams.get(0));
            suggestions.add(createSuggestion(
                    transaction, ws,
                    "Missing Evidence: Property Ownership & Lease Deeds",
                    "Schedule VI of SEBI ICDR requires valid title deeds, registered lease agreements, or NOCs for all manufacturing facilities and the registered corporate office.",
                    SuggestionSeverity.WARNING,
                    "PROPERTY_DOCUMENT"
            ));
        }

        return suggestions;
    }

    private AiSuggestion createSuggestion(Transaction transaction, Workstream ws, String title, String description,
                                          SuggestionSeverity severity, String expectedDocType) {
        UUID targetId = ws != null ? ws.getId() : transaction.getId();
        Map<String, Object> payload = Map.of(
                "expectedDocumentType", expectedDocType,
                "workstreamId", ws != null ? ws.getId().toString() : ""
        );
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = "{}";
        }

        return new AiSuggestion(
                transaction,
                ws,
                null,
                SuggestionType.MISSING_EVIDENCE,
                title,
                description,
                severity,
                "WORKSTREAM",
                targetId,
                "UPLOAD_CHECKLIST",
                payloadJson
        );
    }
}
