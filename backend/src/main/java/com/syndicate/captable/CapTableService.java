package com.syndicate.captable;

import com.syndicate.captable.dto.*;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class CapTableService {

    private final TransactionRepository transactionRepository;
    private final SecurityClassRepository securityClassRepository;
    private final CapTableHolderRepository holderRepository;
    private final ShareTransactionRepository shareTransactionRepository;
    private final ShareEncumbranceRepository shareEncumbranceRepository;
    private final ShareLockInRepository shareLockInRepository;
    private final IpoIssueParametersRepository ipoIssueParametersRepository;
    private final EvidenceRepository evidenceRepository;
    private final HoldingsReplayEngine replayEngine;
    private final LockInCalculator lockInCalculator;
    private final CapTableFactSyncService factSyncService;

    public CapTableService(
            TransactionRepository transactionRepository,
            SecurityClassRepository securityClassRepository,
            CapTableHolderRepository holderRepository,
            ShareTransactionRepository shareTransactionRepository,
            ShareEncumbranceRepository shareEncumbranceRepository,
            ShareLockInRepository shareLockInRepository,
            IpoIssueParametersRepository ipoIssueParametersRepository,
            EvidenceRepository evidenceRepository,
            HoldingsReplayEngine replayEngine,
            LockInCalculator lockInCalculator,
            CapTableFactSyncService factSyncService) {
        this.transactionRepository = transactionRepository;
        this.securityClassRepository = securityClassRepository;
        this.holderRepository = holderRepository;
        this.shareTransactionRepository = shareTransactionRepository;
        this.shareEncumbranceRepository = shareEncumbranceRepository;
        this.shareLockInRepository = shareLockInRepository;
        this.ipoIssueParametersRepository = ipoIssueParametersRepository;
        this.evidenceRepository = evidenceRepository;
        this.replayEngine = replayEngine;
        this.lockInCalculator = lockInCalculator;
        this.factSyncService = factSyncService;
    }

    @Transactional(readOnly = true)
    public CapTableSummaryDto getSummary(UUID transactionId, LocalDate asOfDate) {
        List<SecurityClass> securityClasses = securityClassRepository.findByTransactionId(transactionId);
        List<CapTableHolder> holders = holderRepository.findByTransactionId(transactionId);
        List<ShareTransaction> transactions = shareTransactionRepository.findByTransactionIdOrderByEventDateAscCreatedAtAsc(transactionId);
        List<ShareLockIn> lockIns = shareLockInRepository.findByTransactionId(transactionId);
        List<ShareEncumbrance> encumbrances = shareEncumbranceRepository.findByTransactionId(transactionId);
        IpoIssueParameters ipoParams = ipoIssueParametersRepository.findByTransactionId(transactionId).orElse(null);

        CapTableSummaryDto summary = replayEngine.replay(
                asOfDate,
                securityClasses,
                holders,
                transactions,
                lockIns,
                encumbrances,
                ipoParams
        );

        List<CapTableConflictDto> conflicts = factSyncService.checkFactSync(transactionId, summary);

        return new CapTableSummaryDto(
                summary.asOfDate(),
                summary.totalBasicShares(),
                summary.totalDilutedShares(),
                summary.preIssuePaidUpCapitalInr(),
                summary.freshIssueShares(),
                summary.ofsShares(),
                summary.postIssueShares(),
                summary.postIssuePaidUpCapitalInr(),
                summary.promoterBasicShares(),
                summary.promoterBasicPct(),
                summary.promoterPostIssueShares(),
                summary.promoterPostIssuePct(),
                summary.mpcRequiredShares(),
                summary.mpcShortfallShares(),
                summary.isMpcSatisfied(),
                summary.holdings(),
                summary.lockIns(),
                summary.encumbrances(),
                summary.transactions(),
                summary.ipoParameters(),
                conflicts
        );
    }

    @Transactional
    public SecurityClassDto createSecurityClass(UUID transactionId, SecurityClassDto dto) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        SecurityClass sc = new SecurityClass(
                tx,
                dto.name(),
                dto.securityType(),
                dto.isin(),
                dto.currency(),
                dto.faceValue(),
                dto.votingRightsPerShare(),
                dto.authorizedShares(),
                dto.convertible(),
                dto.conversionRatio(),
                dto.conversionPrice(),
                dto.conversionDeadline()
        );
        return SecurityClassDto.fromEntity(securityClassRepository.save(sc));
    }

    @Transactional
    public CapTableHolderDto createHolder(UUID transactionId, CapTableHolderDto dto) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        CapTableHolder holder = new CapTableHolder(
                tx,
                dto.partyId(),
                dto.legalName(),
                dto.category(),
                dto.pan(),
                dto.dematAccountNo(),
                dto.residentialStatus()
        );
        return CapTableHolderDto.fromEntity(holderRepository.save(holder));
    }

    @Transactional
    public ShareTransactionDto recordShareTransaction(UUID transactionId, ShareTransactionDto dto) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        SecurityClass sc = securityClassRepository.findById(dto.securityClassId())
                .orElseThrow(() -> new NoSuchElementException("Security class not found: " + dto.securityClassId()));

        CapTableHolder src = dto.sourceHolderId() != null
                ? holderRepository.findById(dto.sourceHolderId()).orElse(null)
                : null;

        CapTableHolder dst = dto.destHolderId() != null
                ? holderRepository.findById(dto.destHolderId()).orElse(null)
                : null;

        Evidence evidence = dto.evidenceId() != null
                ? evidenceRepository.findById(dto.evidenceId()).orElse(null)
                : null;

        ShareTransaction st = new ShareTransaction(
                tx,
                sc,
                dto.eventType(),
                dto.eventDate(),
                src,
                dst,
                dto.shareCount(),
                dto.pricePerShare(),
                dto.faceValueAtEvent() != null ? dto.faceValueAtEvent() : sc.getFaceValue(),
                dto.considerationType(),
                dto.boardResolutionDate(),
                dto.shareholderResolutionDate(),
                dto.rocFilingRef(),
                evidence,
                dto.notes()
        );

        return ShareTransactionDto.fromEntity(shareTransactionRepository.save(st));
    }

    @Transactional
    public IpoIssueParametersDto saveIpoParameters(UUID transactionId, IpoIssueParametersDto dto) {
        IpoIssueParameters params = ipoIssueParametersRepository.findByTransactionId(transactionId)
                .orElse(new IpoIssueParameters(transactionId, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null));

        params.setFreshIssueShares(dto.freshIssueShares() != null ? dto.freshIssueShares() : BigDecimal.ZERO);
        params.setOfsShares(dto.ofsShares() != null ? dto.ofsShares() : BigDecimal.ZERO);
        params.setPriceBandLower(dto.priceBandLower());
        params.setPriceBandUpper(dto.priceBandUpper());
        params.setExpectedAllotmentDate(dto.expectedAllotmentDate());

        return IpoIssueParametersDto.fromEntity(ipoIssueParametersRepository.save(params));
    }

    @Transactional
    public ShareEncumbranceDto recordEncumbrance(UUID transactionId, ShareEncumbranceDto dto) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        CapTableHolder holder = holderRepository.findById(dto.holderId())
                .orElseThrow(() -> new NoSuchElementException("Holder not found: " + dto.holderId()));

        SecurityClass sc = securityClassRepository.findById(dto.securityClassId())
                .orElseThrow(() -> new NoSuchElementException("Security class not found: " + dto.securityClassId()));

        Evidence ev = dto.evidenceId() != null ? evidenceRepository.findById(dto.evidenceId()).orElse(null) : null;

        ShareEncumbrance se = new ShareEncumbrance(
                tx,
                holder,
                sc,
                dto.encumberedShares(),
                dto.encumbranceType(),
                dto.pledgeeName(),
                dto.pledgeDate(),
                dto.releaseDate(),
                dto.status(),
                ev
        );

        return ShareEncumbranceDto.fromEntity(shareEncumbranceRepository.save(se));
    }

    @Transactional
    public List<ShareLockInDto> calculateAndPersistLockIns(
            UUID transactionId,
            LocalDate allotmentDate,
            Map<UUID, LockInExemption> exemptions) {

        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));

        CapTableSummaryDto summary = getSummary(transactionId, null);
        List<CapTableHolder> holders = holderRepository.findByTransactionId(transactionId);
        List<SecurityClass> securityClasses = securityClassRepository.findByTransactionId(transactionId);

        List<ShareLockIn> calculated = lockInCalculator.calculateLockIns(
                tx,
                summary,
                holders,
                securityClasses,
                allotmentDate,
                exemptions
        );

        shareLockInRepository.deleteByTransactionId(transactionId);
        List<ShareLockIn> saved = shareLockInRepository.saveAll(calculated);

        return saved.stream().map(ShareLockInDto::fromEntity).toList();
    }
}
