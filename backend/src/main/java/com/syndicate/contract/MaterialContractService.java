package com.syndicate.contract;

import com.syndicate.contract.dto.ContractSummaryDto;
import com.syndicate.contract.dto.MaterialContractDto;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.rpt.RelatedParty;
import com.syndicate.rpt.RelatedPartyRepository;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class MaterialContractService {

    private static final BigDecimal TEN_PCT = new BigDecimal("10.00");

    private final TransactionRepository transactionRepository;
    private final WorkstreamRepository workstreamRepository;
    private final MaterialContractRepository contractRepository;
    private final MaterialContractEvidenceLinkRepository evidenceLinkRepository;
    private final EvidenceRepository evidenceRepository;
    private final RelatedPartyRepository relatedPartyRepository;

    public MaterialContractService(
            TransactionRepository transactionRepository,
            WorkstreamRepository workstreamRepository,
            MaterialContractRepository contractRepository,
            MaterialContractEvidenceLinkRepository evidenceLinkRepository,
            EvidenceRepository evidenceRepository,
            RelatedPartyRepository relatedPartyRepository) {
        this.transactionRepository = transactionRepository;
        this.workstreamRepository = workstreamRepository;
        this.contractRepository = contractRepository;
        this.evidenceLinkRepository = evidenceLinkRepository;
        this.evidenceRepository = evidenceRepository;
        this.relatedPartyRepository = relatedPartyRepository;
    }

    @Transactional(readOnly = true)
    public ContractSummaryDto getContractSummary(UUID transactionId) {
        List<MaterialContract> contracts = contractRepository.findByTransactionId(transactionId);

        int above10Pct = 0;
        int cocPending = 0;
        int exclusivityCount = 0;
        BigDecimal totalAnnualValue = BigDecimal.ZERO;
        List<String> blockers = new ArrayList<>();

        List<MaterialContractDto> dtoList = new ArrayList<>();
        for (MaterialContract c : contracts) {
            if (c.isAbove10PctScreeningIndicator()) {
                above10Pct++;
            }
            if (c.isHasChangeOfControl() && !c.isChangeOfControlConsentObtained()) {
                cocPending++;
                blockers.add("Change of Control consent pending for contract " + c.getContractReference() + " (" + c.getCounterpartyName() + ").");
            }
            if (c.isHasExclusivity()) {
                exclusivityCount++;
            }
            if (c.getContractValueAnnual() != null) {
                totalAnnualValue = totalAnnualValue.add(c.getContractValueAnnual());
            }

            List<UUID> evIds = evidenceLinkRepository.findByIdContractId(c.getId())
                    .stream()
                    .map(l -> l.getEvidence().getId())
                    .toList();

            dtoList.add(MaterialContractDto.fromEntity(c, evIds));
        }

        return new ContractSummaryDto(
                contracts.size(),
                above10Pct,
                cocPending,
                exclusivityCount,
                totalAnnualValue,
                dtoList,
                blockers
        );
    }

    @Transactional
    public MaterialContractDto recordMaterialContract(UUID transactionId, MaterialContractDto dto) {
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

        RelatedParty rp = null;
        if (dto.relatedPartyId() != null) {
            rp = relatedPartyRepository.findById(dto.relatedPartyId()).orElse(null);
        }

        boolean above10Pct = (dto.revenuePercentage() != null && dto.revenuePercentage().compareTo(TEN_PCT) >= 0)
                || (dto.supplyPercentage() != null && dto.supplyPercentage().compareTo(TEN_PCT) >= 0);

        MaterialContract contract = new MaterialContract(
                tx,
                ws,
                dto.contractReference(),
                dto.title(),
                dto.contractCategory(),
                dto.counterpartyName(),
                dto.isRelatedParty(),
                rp,
                dto.executionDate(),
                dto.effectiveDate(),
                dto.expiryDate(),
                dto.isPerpetual(),
                dto.contractValueAnnual(),
                dto.contractValueTotal(),
                dto.revenuePercentage(),
                dto.supplyPercentage(),
                above10Pct,
                dto.hasChangeOfControl(),
                dto.changeOfControlSummary(),
                dto.changeOfControlConsentObtained(),
                dto.hasExclusivity(),
                dto.status(),
                dto.reviewStatus()
        );

        MaterialContract saved = contractRepository.save(contract);

        List<UUID> savedEvIds = new ArrayList<>();
        if (dto.evidenceIds() != null) {
            for (UUID evId : dto.evidenceIds()) {
                Evidence ev = evidenceRepository.findById(evId).orElse(null);
                if (ev != null) {
                    MaterialContractEvidenceLink link = new MaterialContractEvidenceLink(saved, ev, "EXECUTED_CONTRACT");
                    evidenceLinkRepository.save(link);
                    savedEvIds.add(evId);
                }
            }
        }

        return MaterialContractDto.fromEntity(saved, savedEvIds);
    }
}
