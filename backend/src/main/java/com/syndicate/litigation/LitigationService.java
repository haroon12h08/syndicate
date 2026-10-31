package com.syndicate.litigation;

import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.litigation.dto.LitigationCaseDto;
import com.syndicate.litigation.dto.LitigationSummaryDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class LitigationService {

    private final TransactionRepository transactionRepository;
    private final WorkstreamRepository workstreamRepository;
    private final LitigationCaseRepository litigationCaseRepository;
    private final LitigationEvidenceLinkRepository evidenceLinkRepository;
    private final EvidenceRepository evidenceRepository;
    private final LitigationMaterialityService materialityService;

    public LitigationService(
            TransactionRepository transactionRepository,
            WorkstreamRepository workstreamRepository,
            LitigationCaseRepository litigationCaseRepository,
            LitigationEvidenceLinkRepository evidenceLinkRepository,
            EvidenceRepository evidenceRepository,
            LitigationMaterialityService materialityService) {
        this.transactionRepository = transactionRepository;
        this.workstreamRepository = workstreamRepository;
        this.litigationCaseRepository = litigationCaseRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.evidenceRepository = evidenceRepository;
        this.materialityService = materialityService;
    }

    @Transactional(readOnly = true)
    public LitigationSummaryDto getLitigationSummary(UUID transactionId) {
        List<LitigationCase> cases = litigationCaseRepository.findByTransactionId(transactionId);

        BigDecimal totalClaimed = BigDecimal.ZERO;
        BigDecimal totalContingent = BigDecimal.ZERO;
        int materialCount = 0;
        int pendingCriminal = 0;
        int pendingRegulatory = 0;
        List<String> blockers = new ArrayList<>();

        List<LitigationCaseDto> dtoList = new ArrayList<>();
        for (LitigationCase lc : cases) {
            BigDecimal cl = lc.getClaimedAmount() != null ? lc.getClaimedAmount() : BigDecimal.ZERO;
            BigDecimal ct = lc.getContingentLiabilityExposure() != null ? lc.getContingentLiabilityExposure() : BigDecimal.ZERO;

            totalClaimed = totalClaimed.add(cl);
            totalContingent = totalContingent.add(ct);

            if (lc.isMaterial()) {
                materialCount++;
            }

            boolean isPending = "PENDING".equalsIgnoreCase(lc.getStatus()) || "APPEAL_PREFERRED".equalsIgnoreCase(lc.getStatus());
            if ("CRIMINAL_PROCEEDING".equalsIgnoreCase(lc.getCategory()) && isPending) {
                pendingCriminal++;
                blockers.add("Pending Criminal Proceeding against " + lc.getEntityName() + " (" + lc.getCaseNumber() + ") is a SEBI ICDR disclosure blocker.");
            }

            if (("REGULATORY_STATUTORY_ACTION".equalsIgnoreCase(lc.getCategory()) || "EXCHANGE_DISCIPLINARY_ACTION".equalsIgnoreCase(lc.getCategory())) && isPending) {
                pendingRegulatory++;
                blockers.add("Pending Regulatory/Exchange Action (" + lc.getCaseNumber() + ") requires SEBI clearance disclosure.");
            }

            List<UUID> evIds = evidenceLinkRepository.findByIdLitigationId(lc.getId())
                    .stream()
                    .map(l -> l.getEvidence().getId())
                    .toList();

            dtoList.add(LitigationCaseDto.fromEntity(lc, evIds));
        }

        return new LitigationSummaryDto(
                totalClaimed,
                totalContingent,
                cases.size(),
                materialCount,
                pendingCriminal,
                pendingRegulatory,
                dtoList,
                blockers
        );
    }

    @Transactional
    public LitigationCaseDto recordLitigationCase(UUID transactionId, LitigationCaseDto dto) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        Workstream ws = null;
        if (dto.workstreamId() != null) {
            ws = workstreamRepository.findById(dto.workstreamId()).orElse(null);
        }
        if (ws == null) {
            List<Workstream> wsList = workstreamRepository.findByTransactionId(transactionId);
            ws = wsList.stream()
                    .filter(w -> "LEGAL_DUE_DILIGENCE".equalsIgnoreCase(w.getType().name()))
                    .findFirst()
                    .orElse(wsList.isEmpty() ? null : wsList.get(0));
        }
        if (ws == null) {
            throw new IllegalStateException("No workstream found for transaction " + transactionId);
        }

        LitigationCase lc = new LitigationCase(
                tx,
                ws,
                dto.caseNumber(),
                dto.courtForum(),
                dto.jurisdictionCity(),
                dto.caseTitle(),
                dto.category(),
                dto.entityType(),
                dto.entityName(),
                dto.partyRole(),
                dto.oppositeParty(),
                dto.filingDate(),
                dto.claimedAmount(),
                dto.contingentLiabilityExposure(),
                dto.provisionMade(),
                dto.potentialImpact(),
                dto.caseSummary(),
                dto.status(),
                dto.isMaterial(),
                dto.materialityBasis()
        );

        materialityService.evaluateMateriality(lc);
        LitigationCase saved = litigationCaseRepository.save(lc);

        List<UUID> savedEvIds = new ArrayList<>();
        if (dto.evidenceIds() != null) {
            for (UUID evId : dto.evidenceIds()) {
                Evidence ev = evidenceRepository.findById(evId).orElse(null);
                if (ev != null) {
                    LitigationEvidenceLink link = new LitigationEvidenceLink(saved, ev, "COURT_DOCUMENT");
                    evidenceLinkRepository.save(link);
                    savedEvIds.add(evId);
                }
            }
        }

        return LitigationCaseDto.fromEntity(saved, savedEvIds);
    }
}
