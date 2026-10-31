package com.syndicate.rpt;

import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.rpt.dto.RelatedPartyDto;
import com.syndicate.rpt.dto.RelatedPartyTransactionDto;
import com.syndicate.rpt.dto.RptSummaryDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class RelatedPartyService {

    private final TransactionRepository transactionRepository;
    private final RelatedPartyRepository relatedPartyRepository;
    private final RelatedPartyTransactionRepository rptRepository;
    private final RptEvidenceLinkRepository evidenceLinkRepository;
    private final EvidenceRepository evidenceRepository;
    private final RptMaterialityEvaluator materialityEvaluator;

    public RelatedPartyService(
            TransactionRepository transactionRepository,
            RelatedPartyRepository relatedPartyRepository,
            RelatedPartyTransactionRepository rptRepository,
            RptEvidenceLinkRepository evidenceLinkRepository,
            EvidenceRepository evidenceRepository,
            RptMaterialityEvaluator materialityEvaluator) {
        this.transactionRepository = transactionRepository;
        this.relatedPartyRepository = relatedPartyRepository;
        this.rptRepository = rptRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.evidenceRepository = evidenceRepository;
        this.materialityEvaluator = materialityEvaluator;
    }

    @Transactional(readOnly = true)
    public RptSummaryDto getRptSummary(UUID transactionId) {
        List<RelatedParty> parties = relatedPartyRepository.findByTransactionId(transactionId);
        List<RelatedPartyTransaction> txs = rptRepository.findByTransactionId(transactionId);

        BigDecimal totalExposure = BigDecimal.ZERO;
        int materialCount = 0;
        int unapprovedCount = 0;
        List<String> complianceIssues = new ArrayList<>();

        List<RelatedPartyTransactionDto> dtoList = new ArrayList<>();
        for (RelatedPartyTransaction t : txs) {
            BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
            totalExposure = totalExposure.add(amt);

            if (t.isSebiLodrMaterial() || t.isCa2013Sec188Applicable()) {
                materialCount++;
            }

            if ("NONE".equalsIgnoreCase(t.getAuditCommitteeApproval())) {
                unapprovedCount++;
                complianceIssues.add("RPT " + t.getTransactionType() + " (" + amt + ") lacks Audit Committee approval.");
            }

            if ((t.isSebiLodrMaterial() || t.isCa2013Sec188Applicable()) && t.getShareholderApprovalDate() == null) {
                complianceIssues.add("Material RPT " + t.getTransactionType() + " (" + amt + ") lacks required Shareholder approval.");
            }

            List<UUID> evIds = evidenceLinkRepository.findByIdRptId(t.getId())
                    .stream()
                    .map(l -> l.getEvidence().getId())
                    .toList();

            dtoList.add(RelatedPartyTransactionDto.fromEntity(t, evIds));
        }

        return new RptSummaryDto(
                totalExposure,
                txs.size(),
                materialCount,
                unapprovedCount,
                parties.stream().map(RelatedPartyDto::fromEntity).toList(),
                dtoList,
                complianceIssues
        );
    }

    @Transactional
    public RelatedPartyDto createRelatedParty(UUID transactionId, RelatedPartyDto dto) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        RelatedParty rp = new RelatedParty(
                tx,
                dto.name(),
                dto.partyType(),
                dto.pan(),
                dto.dinCin(),
                dto.relationshipNature(),
                dto.relativeDetails(),
                dto.effectiveFrom(),
                dto.effectiveTo(),
                dto.isSuggested()
        );
        return RelatedPartyDto.fromEntity(relatedPartyRepository.save(rp));
    }

    @Transactional
    public RelatedPartyDto confirmSuggestedParty(UUID partyId, User user) {
        RelatedParty rp = relatedPartyRepository.findById(partyId)
                .orElseThrow(() -> new NoSuchElementException("Related party not found: " + partyId));
        rp.setSuggested(false);
        rp.setConfirmedByUser(user);
        rp.setConfirmedAt(Instant.now());
        return RelatedPartyDto.fromEntity(relatedPartyRepository.save(rp));
    }

    @Transactional
    public RelatedPartyTransactionDto recordTransaction(UUID transactionId, RelatedPartyTransactionDto dto) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        RelatedParty party = relatedPartyRepository.findById(dto.relatedPartyId())
                .orElseThrow(() -> new NoSuchElementException("Related party not found: " + dto.relatedPartyId()));

        RelatedPartyTransaction rpt = new RelatedPartyTransaction(
                tx,
                party,
                dto.financialYear(),
                dto.transactionType(),
                dto.amount(),
                dto.currency(),
                dto.inOrdinaryCourse(),
                dto.isArmsLength(),
                dto.armLengthBasis(),
                dto.auditCommitteeApproval(),
                dto.auditCommitteeApprovalDate(),
                dto.auditCommitteeMaxAmount(),
                dto.boardApprovalDate(),
                dto.shareholderApprovalRequired(),
                dto.shareholderApprovalDate(),
                dto.ca2013Sec188Applicable(),
                dto.sebiLodrMaterial()
        );

        materialityEvaluator.evaluate(rpt);
        RelatedPartyTransaction saved = rptRepository.save(rpt);

        List<UUID> savedEvIds = new ArrayList<>();
        if (dto.evidenceIds() != null) {
            for (UUID evId : dto.evidenceIds()) {
                Evidence ev = evidenceRepository.findById(evId).orElse(null);
                if (ev != null) {
                    RptEvidenceLink link = new RptEvidenceLink(saved, ev, "SUPPORTING_EVIDENCE");
                    evidenceLinkRepository.save(link);
                    savedEvIds.add(evId);
                }
            }
        }

        return RelatedPartyTransactionDto.fromEntity(saved, savedEvIds);
    }
}
