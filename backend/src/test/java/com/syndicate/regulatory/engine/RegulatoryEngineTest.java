package com.syndicate.regulatory.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.audit.AuditService;
import com.syndicate.captable.CapTableService;
import com.syndicate.captable.dto.CapTableSummaryDto;
import com.syndicate.contract.MaterialContractRepository;
import com.syndicate.fact.FactRepository;
import com.syndicate.litigation.LitigationCaseRepository;
import com.syndicate.regulatory.engine.dto.ControlEvaluationDto;
import com.syndicate.rpt.RelatedPartyTransactionRepository;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.transaction.TransactionType;
import com.syndicate.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegulatoryEngineTest {

    @Mock private TransactionService transactionService;
    @Mock private RegulatorySnapshotRepository snapshotRepository;
    @Mock private RegulatorySnapshotItemRepository snapshotItemRepository;
    @Mock private RequirementVersionRepository versionRepository;
    @Mock private RegulatoryControlRepository controlRepository;
    @Mock private ControlEvaluationRepository evaluationRepository;
    @Mock private RegulatoryExceptionRepository exceptionRepository;
    @Mock private FactRepository factRepository;
    @Mock private LitigationCaseRepository litigationCaseRepository;
    @Mock private MaterialContractRepository materialContractRepository;
    @Mock private RelatedPartyTransactionRepository rptRepository;
    @Mock private CapTableService capTableService;
    @Mock private AuditService auditService;

    private RegulatoryEngineService engineService;
    private User testUser;
    private Transaction testTransaction;
    private Requirement testReq;
    private RequirementVersion testVer;
    private RegulatoryControl testCtrl;
    private RegulatorySnapshot testSnapshot;

    @BeforeEach
    void setUp() {
        engineService = new RegulatoryEngineService(
                transactionService,
                snapshotRepository,
                snapshotItemRepository,
                versionRepository,
                controlRepository,
                evaluationRepository,
                exceptionRepository,
                factRepository,
                litigationCaseRepository,
                materialContractRepository,
                rptRepository,
                capTableService,
                auditService,
                new ObjectMapper()
        );

        testUser = new User("haroon@trinitycapital.com", "hash", "Haroon Rasheed");
        ReflectionTestUtils.setField(testUser, "id", UUID.randomUUID());

        testTransaction = new Transaction(null, null, TransactionType.SME_IPO, "Trinity Tech SME IPO");
        ReflectionTestUtils.setField(testTransaction, "id", UUID.randomUUID());

        testReq = new Requirement("SEBI_ICDR_REG_238_2025", "SEBI", "INDIA", "MPC 20%", "Lock-in", "SME_IPO", "ALL", "CRITICAL", "ACTIVE");
        ReflectionTestUtils.setField(testReq, "id", UUID.randomUUID());

        testVer = new RequirementVersion(testReq, 1, "SEBI ICDR Reg 238", "https://sebi.gov.in", LocalDate.of(2025, 1, 1), testUser);
        ReflectionTestUtils.setField(testVer, "id", UUID.randomUUID());

        testCtrl = new RegulatoryControl(testVer, "CTRL_SEBI_MPC_20PCT", "MPC 20%", "Desc", "DOMAIN_RECONCILIATION", "{}", "CRITICAL", 1);
        ReflectionTestUtils.setField(testCtrl, "id", UUID.randomUUID());

        testSnapshot = new RegulatorySnapshot(testTransaction, "Standard Snapshot", LocalDate.of(2026, 1, 1), testUser, "Notes");
        ReflectionTestUtils.setField(testSnapshot, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("Evaluate transaction control adheres to Invariant 18 / §13 wording boundary (never asserts compliance)")
    void evaluateControl_obeysWordingBoundary_andDetectsMpcShortfall() {
        when(transactionService.findTransaction(testTransaction.getId())).thenReturn(testTransaction);
        when(snapshotRepository.findFirstByTransactionIdAndActiveTrueOrderByPinnedAtDesc(testTransaction.getId()))
                .thenReturn(Optional.of(testSnapshot));
        when(snapshotItemRepository.findBySnapshotId(testSnapshot.getId()))
                .thenReturn(List.of(new RegulatorySnapshotItem(testSnapshot, testVer)));
        when(controlRepository.findByRequirementVersionIdOrderByOrderIndexAsc(testVer.getId()))
                .thenReturn(List.of(testCtrl));

        // Mock Cap table with 18.5% promoter post-issue holding (< 20%)
        CapTableSummaryDto summary = new CapTableSummaryDto(
                LocalDate.now(), BigDecimal.valueOf(10000000), BigDecimal.valueOf(10000000),
                BigDecimal.valueOf(100000000), BigDecimal.valueOf(2000000), BigDecimal.ZERO,
                BigDecimal.valueOf(12000000), BigDecimal.valueOf(120000000),
                BigDecimal.valueOf(2220000), BigDecimal.valueOf(18.5),
                BigDecimal.valueOf(2220000), BigDecimal.valueOf(18.5),
                BigDecimal.valueOf(2400000), BigDecimal.valueOf(180000),
                false, List.of(), List.of(), List.of(), List.of(), null, List.of()
        );
        when(capTableService.getSummary(eq(testTransaction.getId()), any(LocalDate.class))).thenReturn(summary);
        when(evaluationRepository.findByTransactionIdAndControlId(testTransaction.getId(), testCtrl.getId()))
                .thenReturn(Optional.empty());
        when(evaluationRepository.save(any(ControlEvaluation.class))).thenAnswer(i -> i.getArgument(0));

        List<ControlEvaluationDto> result = engineService.evaluateTransaction(testTransaction.getId(), testUser);

        assertThat(result).hasSize(1);
        ControlEvaluationDto eval = result.get(0);
        assertThat(eval.status()).isEqualTo("FAIL");
        // Verify Invariant 18 wording boundary:
        assertThat(eval.explanation())
                .startsWith("Based on configured rule CTRL_SEBI_MPC_20PCT v1 and current data, evaluates to FAIL.")
                .doesNotContain("compliant")
                .doesNotContain("SEBI will approve");
    }

    @Test
    @DisplayName("Active approved exception overrides FAIL to EXCEPTION with transparent mitigation trace (spec §39, invariant 19)")
    void activeApprovedException_overridesFailToException() {
        when(transactionService.findTransaction(testTransaction.getId())).thenReturn(testTransaction);
        when(snapshotRepository.findFirstByTransactionIdAndActiveTrueOrderByPinnedAtDesc(testTransaction.getId()))
                .thenReturn(Optional.of(testSnapshot));
        when(snapshotItemRepository.findBySnapshotId(testSnapshot.getId()))
                .thenReturn(List.of(new RegulatorySnapshotItem(testSnapshot, testVer)));
        when(controlRepository.findByRequirementVersionIdOrderByOrderIndexAsc(testVer.getId()))
                .thenReturn(List.of(testCtrl));

        // Deficient MPC holding
        CapTableSummaryDto summary = new CapTableSummaryDto(
                LocalDate.now(), BigDecimal.valueOf(10000000), BigDecimal.valueOf(10000000),
                BigDecimal.valueOf(100000000), BigDecimal.valueOf(2000000), BigDecimal.ZERO,
                BigDecimal.valueOf(12000000), BigDecimal.valueOf(120000000),
                BigDecimal.valueOf(2220000), BigDecimal.valueOf(18.5),
                BigDecimal.valueOf(2220000), BigDecimal.valueOf(18.5),
                BigDecimal.valueOf(2400000), BigDecimal.valueOf(180000),
                false, List.of(), List.of(), List.of(), List.of(), null, List.of()
        );
        when(capTableService.getSummary(eq(testTransaction.getId()), any(LocalDate.class))).thenReturn(summary);

        // Approved active regulatory exception exists
        RegulatoryException exception = new RegulatoryException(
                testTransaction, testCtrl, testVer,
                "Promoter undertaking to subscribe to shortfall within 7 days",
                "READINESS_REVIEW", null, "MEDIUM", testUser, LocalDate.now().plusMonths(1),
                "Escrow account established for supplementary contribution"
        );
        exception.setStatus("APPROVED");
        ReflectionTestUtils.setField(exception, "id", UUID.randomUUID());

        when(exceptionRepository.findFirstByTransactionIdAndControlIdAndStatus(testTransaction.getId(), testCtrl.getId(), "APPROVED"))
                .thenReturn(Optional.of(exception));
        when(evaluationRepository.findByTransactionIdAndControlId(testTransaction.getId(), testCtrl.getId()))
                .thenReturn(Optional.empty());
        when(evaluationRepository.save(any(ControlEvaluation.class))).thenAnswer(i -> i.getArgument(0));

        List<ControlEvaluationDto> result = engineService.evaluateTransaction(testTransaction.getId(), testUser);

        assertThat(result).hasSize(1);
        ControlEvaluationDto eval = result.get(0);
        assertThat(eval.status()).isEqualTo("EXCEPTION");
        assertThat(eval.exceptionId()).isEqualTo(exception.getId());
        assertThat(eval.explanation())
                .contains("evaluates to EXCEPTION. Overridden by approved exception")
                .contains("Escrow account established");
    }
}
