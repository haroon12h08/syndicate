package com.syndicate.rpt;

import com.syndicate.contract.MaterialContract;
import com.syndicate.contract.MaterialContractService;
import com.syndicate.contract.dto.ContractSummaryDto;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.litigation.LitigationCase;
import com.syndicate.litigation.LitigationMaterialityService;
import com.syndicate.regulatory.RegulatoryRule;
import com.syndicate.regulatory.RegulatoryRuleRepository;
import com.syndicate.regulatory.RuleEvaluation;
import com.syndicate.regulatory.RuleEvaluationRepository;
import com.syndicate.regulatory.RuleEvaluationStatus;
import com.syndicate.transaction.Transaction;
import com.syndicate.workstream.Workstream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DomainRegistersTest {

    private RegulatoryRuleRepository regulatoryRuleRepository;
    private RuleEvaluationRepository ruleEvaluationRepository;
    private FactRepository factRepository;
    private RptMaterialityEvaluator rptEvaluator;
    private LitigationMaterialityService litigationEvaluator;

    private Transaction mockTx;
    private RelatedParty relatedParty;

    @BeforeEach
    void setUp() throws Exception {
        regulatoryRuleRepository = mock(RegulatoryRuleRepository.class);
        ruleEvaluationRepository = mock(RuleEvaluationRepository.class);
        factRepository = mock(FactRepository.class);

        rptEvaluator = new RptMaterialityEvaluator(regulatoryRuleRepository, ruleEvaluationRepository, factRepository);
        litigationEvaluator = new LitigationMaterialityService(factRepository);

        mockTx = mock(Transaction.class);
        UUID txId = UUID.randomUUID();
        when(mockTx.getId()).thenReturn(txId);

        relatedParty = new RelatedParty(
                mockTx, "Trinity Holdings LLC", "BODY_CORPORATE",
                "ABCDE1234F", "U12345DL2020PTC123456", "PROMOTER",
                null, LocalDate.of(2020, 1, 1), null, false
        );
        setId(relatedParty, UUID.randomUUID());

        // Setup mock facts for turnover (₹100 Cr) and Net Worth (₹50 Cr)
        Fact turnoverFact = mock(Fact.class);
        when(turnoverFact.getFactKey()).thenReturn("issuer.revenue");
        when(turnoverFact.getValue()).thenReturn("1000000000.00"); // 100 Crore

        Fact netWorthFact = mock(Fact.class);
        when(netWorthFact.getFactKey()).thenReturn("issuer.net_worth");
        when(netWorthFact.getValue()).thenReturn("500000000.00"); // 50 Crore

        when(factRepository.findByWorkstreamTransactionId(txId)).thenReturn(List.of(turnoverFact, netWorthFact));
    }

    private void setId(Object entity, UUID id) throws Exception {
        Field f = entity.getClass().getSuperclass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    @Test
    @DisplayName("Companies Act 2013 §188 Proviso: Transactions in ordinary course and at arm length are exempt even if >10% turnover")
    void testOrdinaryCourseArmsLengthExemption() {
        // Amount: ₹15 Crore (> 10% of ₹100 Crore turnover)
        // BUT in ordinary course and arm's length
        RelatedPartyTransaction rpt = new RelatedPartyTransaction(
                mockTx, relatedParty, "FY2025-26", "SALE_OF_GOODS",
                new BigDecimal("150000000.00"), "INR",
                true, // in ordinary course
                true, // arm's length
                "Comparable Uncontrolled Price (CUP) Transfer Pricing Study",
                "OMNIBUS", LocalDate.of(2025, 4, 1), new BigDecimal("200000000.00"),
                LocalDate.of(2025, 4, 2), false, null, false, false
        );

        rptEvaluator.evaluate(rpt);

        // Section 188 should NOT apply due to statutory 2nd proviso exemption
        assertThat(rpt.isCa2013Sec188Applicable()).isFalse();
        // However, under SEBI LODR Reg 23(4), ₹15 Cr is 15% of turnover (> 10%), so SEBI LODR is material!
        assertThat(rpt.isSebiLodrMaterial()).isTrue();
    }

    @Test
    @DisplayName("Non-exempt RPT >10% turnover without shareholder approval triggers RuleEvaluation FAILED")
    void testNonExemptRptViolation() throws Exception {
        RegulatoryRule caRule = mock(RegulatoryRule.class);
        when(caRule.getId()).thenReturn(UUID.randomUUID());
        when(caRule.getCode()).thenReturn("CA2013_SEC_188_GOODS");
        when(regulatoryRuleRepository.findByCode("CA2013_SEC_188_GOODS")).thenReturn(Optional.of(caRule));

        // Amount: ₹12 Crore (> 10% of ₹100 Crore turnover), NOT in ordinary course
        RelatedPartyTransaction rpt = new RelatedPartyTransaction(
                mockTx, relatedParty, "FY2025-26", "SALE_OF_GOODS",
                new BigDecimal("120000000.00"), "INR",
                false, // NOT in ordinary course
                false, // NOT arm's length
                null,
                "NONE", null, null,
                null, false, null, false, false
        );
        setId(rpt, UUID.randomUUID());

        rptEvaluator.evaluate(rpt);

        assertThat(rpt.isCa2013Sec188Applicable()).isTrue();
        assertThat(rpt.isShareholderApprovalRequired()).isTrue();

        ArgumentCaptor<RuleEvaluation> evalCaptor = ArgumentCaptor.forClass(RuleEvaluation.class);
        verify(ruleEvaluationRepository, atLeastOnce()).save(evalCaptor.capture());
        RuleEvaluation caEval = evalCaptor.getAllValues().stream()
                .filter(e -> "CA2013_SEC_188_GOODS".equals(e.getRule().getCode()))
                .findFirst().orElseThrow();

        assertThat(caEval.getStatus()).isEqualTo(RuleEvaluationStatus.FAILED);
    }

    @Test
    @DisplayName("Litigation Materiality: Criminal proceedings are mandatory material regardless of financial exposure")
    void testCriminalProceedingMandatoryMateriality() {
        Workstream mockWs = mock(Workstream.class);
        LitigationCase criminalCase = new LitigationCase(
                mockTx, mockWs, "CR/2024/001", "Chief Metropolitan Magistrate Court, Mumbai",
                "Mumbai", "State of Maharashtra vs. Director", "CRIMINAL_PROCEEDING",
                "DIRECTOR", "Haroon Trinity", "RESPONDENT_DEFENDANT", "State of Maharashtra",
                LocalDate.of(2024, 3, 15), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "REPUTATIONAL", "Alleged non-compliance with municipal zoning permissions",
                "PENDING", false, null
        );

        litigationEvaluator.evaluateMateriality(criminalCase);

        assertThat(criminalCase.isMaterial()).isTrue();
        assertThat(criminalCase.getMaterialityBasis()).isEqualTo("CRIMINAL_MANDATORY");
    }

    @Test
    @DisplayName("Litigation Materiality: Civil litigation evaluated against Net Worth (5%) and qualitative impacts")
    void testCivilLitigationMateriality() {
        Workstream mockWs = mock(Workstream.class);

        // Case 1: Claim ₹3 Crore (> 5% of ₹50 Crore Net Worth = ₹2.5 Crore) -> Material
        LitigationCase materialCivil = new LitigationCase(
                mockTx, mockWs, "CS/2023/100", "High Court of Delhi", "New Delhi",
                "Vendor Corp vs. Issuer", "MATERIAL_CIVIL_LITIGATION",
                "ISSUER", "Trinity Capital", "RESPONDENT_DEFENDANT", "Vendor Corp",
                LocalDate.of(2023, 5, 1), new BigDecimal("30000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "FINANCIAL", "Commercial breach of contract suit", "PENDING", false, null
        );
        litigationEvaluator.evaluateMateriality(materialCivil);
        assertThat(materialCivil.isMaterial()).isTrue();
        assertThat(materialCivil.getMaterialityBasis()).isEqualTo("NET_WORTH_THRESHOLD");

        // Case 2: Claim ₹50 Lakh (< 5% Net Worth and < 2% Turnover) with purely FINANCIAL impact -> Non-material
        LitigationCase immaterialCivil = new LitigationCase(
                mockTx, mockWs, "CS/2023/101", "City Civil Court, Bengaluru", "Bengaluru",
                "Supplier vs. Issuer", "MATERIAL_CIVIL_LITIGATION",
                "ISSUER", "Trinity Capital", "RESPONDENT_DEFENDANT", "Supplier",
                LocalDate.of(2023, 7, 1), new BigDecimal("5000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "FINANCIAL", "Minor invoice dispute", "PENDING", false, null
        );
        litigationEvaluator.evaluateMateriality(immaterialCivil);
        assertThat(immaterialCivil.isMaterial()).isFalse();
        assertThat(immaterialCivil.getMaterialityBasis()).isEqualTo("BELOW_THRESHOLDS");

        // Case 3: Same minor ₹50 Lakh claim, but impact is PERMIT_REVOCATION -> Qualitative Materiality!
        LitigationCase permitRiskCivil = new LitigationCase(
                mockTx, mockWs, "CS/2023/102", "National Green Tribunal", "New Delhi",
                "NGO vs. Issuer", "MATERIAL_CIVIL_LITIGATION",
                "ISSUER", "Trinity Capital", "RESPONDENT_DEFENDANT", "NGO",
                LocalDate.of(2023, 8, 1), new BigDecimal("5000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "PERMIT_REVOCATION", "Environmental clearance challenge", "PENDING", false, null
        );
        litigationEvaluator.evaluateMateriality(permitRiskCivil);
        assertThat(permitRiskCivil.isMaterial()).isTrue();
        assertThat(permitRiskCivil.getMaterialityBasis()).isEqualTo("QUALITATIVE_OPERATION");
    }
}
