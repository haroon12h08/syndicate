package com.syndicate.captable;

import com.syndicate.captable.dto.CapTableSummaryDto;
import com.syndicate.captable.dto.HolderShareholdingDto;
import com.syndicate.regulatory.RegulatoryRule;
import com.syndicate.regulatory.RegulatoryRuleRepository;
import com.syndicate.regulatory.RuleEvaluation;
import com.syndicate.regulatory.RuleEvaluationRepository;
import com.syndicate.regulatory.RuleEvaluationStatus;
import com.syndicate.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapTableEngineTest {

    private HoldingsReplayEngine replayEngine;
    private LockInCalculator lockInCalculator;
    private RegulatoryRuleRepository regulatoryRuleRepository;
    private RuleEvaluationRepository ruleEvaluationRepository;

    private Transaction mockTx;
    private SecurityClass equityClass;
    private SecurityClass ccpsClass;
    private CapTableHolder promoterHolder;
    private CapTableHolder angelHolder;
    private CapTableHolder esopTrustHolder;

    @BeforeEach
    void setUp() throws Exception {
        replayEngine = new HoldingsReplayEngine();
        regulatoryRuleRepository = mock(RegulatoryRuleRepository.class);
        ruleEvaluationRepository = mock(RuleEvaluationRepository.class);
        lockInCalculator = new LockInCalculator(regulatoryRuleRepository, ruleEvaluationRepository);

        mockTx = mock(Transaction.class);
        UUID txId = UUID.randomUUID();
        when(mockTx.getId()).thenReturn(txId);

        equityClass = new SecurityClass(
                mockTx, "Equity Shares", SecurityType.EQUITY, "INE123456789", "INR",
                new BigDecimal("10.00"), BigDecimal.ONE, new BigDecimal("100000000"),
                false, BigDecimal.ONE, null, null
        );
        setId(equityClass, UUID.randomUUID());

        ccpsClass = new SecurityClass(
                mockTx, "Series A CCPS", SecurityType.PREFERENCE_CCPS, "INE987654321", "INR",
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("1000000"),
                true, new BigDecimal("2.000000"), new BigDecimal("50.00"), LocalDate.of(2028, 12, 31)
        );
        setId(ccpsClass, UUID.randomUUID());

        promoterHolder = new CapTableHolder(
                mockTx, null, "Haroon Trinity", HolderCategory.PROMOTER,
                "ABCDE1234F", "1208160012345678", "RESIDENT_INDIAN"
        );
        setId(promoterHolder, UUID.randomUUID());

        angelHolder = new CapTableHolder(
                mockTx, null, "Angel Investor", HolderCategory.PUBLIC_INDIVIDUAL,
                "FGHIJ5678K", "1208160087654321", "RESIDENT_INDIAN"
        );
        setId(angelHolder, UUID.randomUUID());

        esopTrustHolder = new CapTableHolder(
                mockTx, null, "Trinity ESOP Trust", HolderCategory.ESOP_TRUST,
                "KLMNO9012P", "1208160099999999", "RESIDENT_INDIAN"
        );
        setId(esopTrustHolder, UUID.randomUUID());
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field f = entity.getClass().getSuperclass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    @Test
    @DisplayName("Point-in-time holdings replay calculates basic and fully diluted ownership accurately")
    void testBasicAndDilutedReplay() throws Exception {
        List<ShareTransaction> txList = new ArrayList<>();

        // Incorporation: 5,000,000 equity shares to promoter
        ShareTransaction inc = new ShareTransaction(
                mockTx, equityClass, CorporateActionType.INCORPORATION, LocalDate.of(2022, 1, 1),
                null, promoterHolder, new BigDecimal("5000000"), new BigDecimal("10.00"),
                new BigDecimal("10.00"), ConsiderationType.CASH_BANK, null, null, null, null, "Seed incorporation"
        );
        setId(inc, UUID.randomUUID());
        txList.add(inc);

        // Preferential: 500,000 CCPS to angel (convertible 2:1 into 1,000,000 equity)
        ShareTransaction ccpsAllotment = new ShareTransaction(
                mockTx, ccpsClass, CorporateActionType.ALLOTMENT_PREFERENTIAL, LocalDate.of(2023, 6, 1),
                null, angelHolder, new BigDecimal("500000"), new BigDecimal("100.00"),
                new BigDecimal("100.00"), ConsiderationType.CASH_BANK, null, null, null, null, "Series A"
        );
        setId(ccpsAllotment, UUID.randomUUID());
        txList.add(ccpsAllotment);

        IpoIssueParameters ipoParams = new IpoIssueParameters(
                mockTx.getId(),
                new BigDecimal("2000000"), // fresh issue: 2,000,000
                new BigDecimal("1000000"), // OFS: 1,000,000 from promoter
                new BigDecimal("150.00"),
                new BigDecimal("160.00"),
                LocalDate.of(2026, 11, 1)
        );

        CapTableSummaryDto summary = replayEngine.replay(
                null,
                List.of(equityClass, ccpsClass),
                List.of(promoterHolder, angelHolder),
                txList,
                Collections.emptyList(),
                Collections.emptyList(),
                ipoParams
        );

        // Basic Equity: promoter = 5,000,000, angel = 0 (only CCPS)
        assertThat(summary.totalBasicShares()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(summary.promoterBasicShares()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(summary.promoterBasicPct()).isEqualByComparingTo(new BigDecimal("100.0000"));

        // Fully Diluted: promoter = 5,000,000, angel = 500,000 * 2.0 = 1,000,000 -> Total = 6,000,000
        assertThat(summary.totalDilutedShares()).isEqualByComparingTo(new BigDecimal("6000000"));

        // Post-Issue: total = basic(5M) + fresh(2M) = 7,000,000
        assertThat(summary.postIssueShares()).isEqualByComparingTo(new BigDecimal("7000000"));
        // Promoter post-issue: basic(5M) - OFS(1M) = 4,000,000 (57.14%)
        assertThat(summary.promoterPostIssueShares()).isEqualByComparingTo(new BigDecimal("4000000"));

        // MPC required: 20% of 7M = 1,400,000
        assertThat(summary.mpcRequiredShares()).isEqualByComparingTo(new BigDecimal("1400000"));
        assertThat(summary.isMpcSatisfied()).isTrue();
        assertThat(summary.mpcShortfallShares()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Historical point-in-time replay: queries before vs after secondary transfer")
    void testHistoricalReconstruction() throws Exception {
        List<ShareTransaction> txList = new ArrayList<>();

        ShareTransaction t1 = new ShareTransaction(
                mockTx, equityClass, CorporateActionType.INCORPORATION, LocalDate.of(2023, 1, 1),
                null, promoterHolder, new BigDecimal("1000000"), new BigDecimal("10.00"),
                new BigDecimal("10.00"), ConsiderationType.CASH_BANK, null, null, null, null, null
        );
        setId(t1, UUID.randomUUID());
        txList.add(t1);

        ShareTransaction t2 = new ShareTransaction(
                mockTx, equityClass, CorporateActionType.TRANSFER, LocalDate.of(2024, 1, 1),
                promoterHolder, angelHolder, new BigDecimal("200000"), new BigDecimal("25.00"),
                new BigDecimal("10.00"), ConsiderationType.CASH_BANK, null, null, null, null, null
        );
        setId(t2, UUID.randomUUID());
        txList.add(t2);

        // Point-in-time as of 2023-06-01 (before transfer): Promoter should have 1,000,000 shares
        CapTableSummaryDto asOf2023 = replayEngine.replay(
                LocalDate.of(2023, 6, 1),
                List.of(equityClass),
                List.of(promoterHolder, angelHolder),
                txList,
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );
        assertThat(asOf2023.totalBasicShares()).isEqualByComparingTo(new BigDecimal("1000000"));
        HolderShareholdingDto prom2023 = asOf2023.holdings().stream().filter(h -> h.holderId().equals(promoterHolder.getId())).findFirst().get();
        assertThat(prom2023.basicShares()).isEqualByComparingTo(new BigDecimal("1000000"));

        // Point-in-time as of 2025-01-01 (after transfer): Promoter = 800,000, Angel = 200,000
        CapTableSummaryDto asOf2025 = replayEngine.replay(
                LocalDate.of(2025, 1, 1),
                List.of(equityClass),
                List.of(promoterHolder, angelHolder),
                txList,
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );
        HolderShareholdingDto prom2025 = asOf2025.holdings().stream().filter(h -> h.holderId().equals(promoterHolder.getId())).findFirst().get();
        HolderShareholdingDto angel2025 = asOf2025.holdings().stream().filter(h -> h.holderId().equals(angelHolder.getId())).findFirst().get();
        assertThat(prom2025.basicShares()).isEqualByComparingTo(new BigDecimal("800000"));
        assertThat(angel2025.basicShares()).isEqualByComparingTo(new BigDecimal("200000"));
    }

    @Test
    @DisplayName("Negative Test: Overdraft protection throws IllegalStateException when transferring more shares than owned")
    void testOverdraftProtection() throws Exception {
        List<ShareTransaction> txList = new ArrayList<>();

        ShareTransaction t1 = new ShareTransaction(
                mockTx, equityClass, CorporateActionType.INCORPORATION, LocalDate.of(2023, 1, 1),
                null, promoterHolder, new BigDecimal("100000"), new BigDecimal("10.00"),
                new BigDecimal("10.00"), ConsiderationType.CASH_BANK, null, null, null, null, null
        );
        setId(t1, UUID.randomUUID());
        txList.add(t1);

        // Attempt to transfer 150,000 shares when only 100,000 exist
        ShareTransaction invalidTransfer = new ShareTransaction(
                mockTx, equityClass, CorporateActionType.TRANSFER, LocalDate.of(2024, 1, 1),
                promoterHolder, angelHolder, new BigDecimal("150000"), new BigDecimal("20.00"),
                new BigDecimal("10.00"), ConsiderationType.CASH_BANK, null, null, null, null, null
        );
        setId(invalidTransfer, UUID.randomUUID());
        txList.add(invalidTransfer);

        assertThatThrownBy(() -> replayEngine.replay(
                null,
                List.of(equityClass),
                List.of(promoterHolder, angelHolder),
                txList,
                Collections.emptyList(),
                Collections.emptyList(),
                null
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient shares");
    }

    @Test
    @DisplayName("SEBI ICDR 2025 Amendment Tiered Lock-in: 20% MPC for 3yr, excess tiered 50% for 2yr and 50% for 1yr")
    void test2025AmendedPromoterLockIn() {
        RegulatoryRule rule238 = mock(RegulatoryRule.class);
        UUID ruleId = UUID.randomUUID();
        when(rule238.getId()).thenReturn(ruleId);
        when(rule238.getCode()).thenReturn("SEBI_ICDR_REG_238_2025");
        when(regulatoryRuleRepository.findByCode("SEBI_ICDR_REG_238_2025")).thenReturn(Optional.of(rule238));

        // Post issue: 10,000,000 shares. Promoter has 6,000,000 post-issue (60%).
        // 20% MPC = 2,000,000 shares.
        // Excess = 4,000,000 shares.
        // 50% of excess = 2,000,000 shares for 2 years.
        // Remaining 50% = 2,000,000 shares for 1 year.
        HolderShareholdingDto ph = new HolderShareholdingDto(
                promoterHolder.getId(), promoterHolder.getLegalName(), HolderCategory.PROMOTER,
                new BigDecimal("6000000"), new BigDecimal("60.00"),
                new BigDecimal("6000000"), new BigDecimal("60.00"),
                new BigDecimal("6000000"), new BigDecimal("60.00"),
                BigDecimal.ZERO, BigDecimal.ZERO
        );

        CapTableSummaryDto summary = new CapTableSummaryDto(
                LocalDate.of(2026, 11, 1),
                new BigDecimal("10000000"),
                new BigDecimal("10000000"),
                new BigDecimal("100000000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10000000"),
                new BigDecimal("100000000.00"),
                new BigDecimal("6000000"), new BigDecimal("60.00"),
                new BigDecimal("6000000"), new BigDecimal("60.00"),
                new BigDecimal("2000000"), BigDecimal.ZERO, true,
                List.of(ph),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList()
        );

        LocalDate allotment = LocalDate.of(2026, 11, 1);
        List<ShareLockIn> lockIns = lockInCalculator.calculateLockIns(
                mockTx, summary, List.of(promoterHolder), List.of(equityClass), allotment, Collections.emptyMap()
        );

        assertThat(lockIns).hasSize(3);

        ShareLockIn mpcLock = lockIns.stream()
                .filter(l -> l.getLockInCategory() == LockInTier.PROMOTER_MINIMUM_CONTRIBUTION_3YR)
                .findFirst().orElseThrow();
        assertThat(mpcLock.getLockedShareCount()).isEqualByComparingTo(new BigDecimal("2000000"));
        assertThat(mpcLock.getExpiryDate()).isEqualTo(allotment.plusYears(3));

        ShareLockIn excess2yr = lockIns.stream()
                .filter(l -> l.getLockInCategory() == LockInTier.PROMOTER_EXCESS_2YR)
                .findFirst().orElseThrow();
        assertThat(excess2yr.getLockedShareCount()).isEqualByComparingTo(new BigDecimal("2000000"));
        assertThat(excess2yr.getExpiryDate()).isEqualTo(allotment.plusYears(2));

        ShareLockIn excess1yr = lockIns.stream()
                .filter(l -> l.getLockInCategory() == LockInTier.PROMOTER_EXCESS_1YR)
                .findFirst().orElseThrow();
        assertThat(excess1yr.getLockedShareCount()).isEqualByComparingTo(new BigDecimal("2000000"));
        assertThat(excess1yr.getExpiryDate()).isEqualTo(allotment.plusYears(1));

        // Verify RuleEvaluation saved with status PASSED
        ArgumentCaptor<RuleEvaluation> evalCaptor = ArgumentCaptor.forClass(RuleEvaluation.class);
        verify(ruleEvaluationRepository).save(evalCaptor.capture());
        assertThat(evalCaptor.getValue().getStatus()).isEqualTo(RuleEvaluationStatus.PASSED);
    }

    @Test
    @DisplayName("SEBI ICDR Reg 239: Non-promoter pre-issue capital locked for 1 year, ESOP trust exempted")
    void testReg239NonPromoterLockInAndExemption() {
        RegulatoryRule rule239 = mock(RegulatoryRule.class);
        when(rule239.getId()).thenReturn(UUID.randomUUID());
        when(regulatoryRuleRepository.findByCode("SEBI_ICDR_REG_239")).thenReturn(Optional.of(rule239));

        HolderShareholdingDto angelHolding = new HolderShareholdingDto(
                angelHolder.getId(), angelHolder.getLegalName(), HolderCategory.PUBLIC_INDIVIDUAL,
                new BigDecimal("500000"), new BigDecimal("5.00"),
                new BigDecimal("500000"), new BigDecimal("5.00"),
                new BigDecimal("500000"), new BigDecimal("5.00"),
                BigDecimal.ZERO, BigDecimal.ZERO
        );

        HolderShareholdingDto esopHolding = new HolderShareholdingDto(
                esopTrustHolder.getId(), esopTrustHolder.getLegalName(), HolderCategory.ESOP_TRUST,
                new BigDecimal("200000"), new BigDecimal("2.00"),
                new BigDecimal("200000"), new BigDecimal("2.00"),
                new BigDecimal("200000"), new BigDecimal("2.00"),
                BigDecimal.ZERO, BigDecimal.ZERO
        );

        CapTableSummaryDto summary = new CapTableSummaryDto(
                LocalDate.of(2026, 11, 1),
                new BigDecimal("10000000"),
                new BigDecimal("10000000"),
                new BigDecimal("100000000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10000000"),
                new BigDecimal("100000000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("2000000"), new BigDecimal("2000000"), false,
                List.of(angelHolding, esopHolding),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList()
        );

        LocalDate allotment = LocalDate.of(2026, 11, 1);
        List<ShareLockIn> lockIns = lockInCalculator.calculateLockIns(
                mockTx, summary, List.of(angelHolder, esopTrustHolder), List.of(equityClass), allotment, Collections.emptyMap()
        );

        // Angel investor: 1 year statutory lock-in
        ShareLockIn angelLock = lockIns.stream()
                .filter(l -> l.getHolder().getId().equals(angelHolder.getId()))
                .findFirst().orElseThrow();
        assertThat(angelLock.getLockInCategory()).isEqualTo(LockInTier.PRE_ISSUE_NON_PROMOTER_1YR);
        assertThat(angelLock.isStatutory()).isTrue();
        assertThat(angelLock.getExpiryDate()).isEqualTo(allotment.plusYears(1));

        // ESOP trust: statutory exemption detected
        ShareLockIn esopLock = lockIns.stream()
                .filter(l -> l.getHolder().getId().equals(esopTrustHolder.getId()))
                .findFirst().orElseThrow();
        assertThat(esopLock.getExemptionClaimed()).isEqualTo(LockInExemption.ESOP_EXEMPTION);
        assertThat(esopLock.isStatutory()).isFalse();
    }
}
