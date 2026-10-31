package com.syndicate.rpt;

import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.regulatory.RegulatoryRule;
import com.syndicate.regulatory.RegulatoryRuleRepository;
import com.syndicate.regulatory.RuleEvaluation;
import com.syndicate.regulatory.RuleEvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Component
public class RptMaterialityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RptMaterialityEvaluator.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE_THOUSAND_CRORE = new BigDecimal("10000000000.00");

    private final RegulatoryRuleRepository regulatoryRuleRepository;
    private final RuleEvaluationRepository ruleEvaluationRepository;
    private final FactRepository factRepository;

    public RptMaterialityEvaluator(
            RegulatoryRuleRepository regulatoryRuleRepository,
            RuleEvaluationRepository ruleEvaluationRepository,
            FactRepository factRepository) {
        this.regulatoryRuleRepository = regulatoryRuleRepository;
        this.ruleEvaluationRepository = ruleEvaluationRepository;
        this.factRepository = factRepository;
    }

    public void evaluate(RelatedPartyTransaction rpt) {
        BigDecimal amount = rpt.getAmount();
        if (amount == null) return;

        // Fetch turnover fact if present
        BigDecimal turnover = BigDecimal.ZERO;
        List<Fact> facts = factRepository.findByWorkstreamTransactionId(rpt.getTransaction().getId());
        for (Fact f : facts) {
            if ("issuer.revenue".equalsIgnoreCase(f.getFactKey()) || "issuer.turnover".equalsIgnoreCase(f.getFactKey())) {
                try {
                    turnover = new BigDecimal(f.getValue().trim().replaceAll("[^0-9.]", ""));
                    break;
                } catch (Exception ignored) {}
            }
        }

        // 1. Companies Act 2013 Section 188 Evaluation
        boolean caApplicable = false;
        if (!rpt.isInOrdinaryCourse() || !rpt.isArmsLength()) {
            // Not exempt under Section 188 proviso
            Optional<RegulatoryRule> caRule = regulatoryRuleRepository.findByCode("CA2013_SEC_188_GOODS");
            BigDecimal caThresholdPct = new BigDecimal("10.0"); // from versioned rule
            if (turnover.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal limit = turnover.multiply(caThresholdPct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (amount.compareTo(limit) >= 0) {
                    caApplicable = true;
                    if (caRule.isPresent()) {
                        com.syndicate.regulatory.RuleEvaluationStatus status = rpt.getShareholderApprovalDate() != null
                                ? com.syndicate.regulatory.RuleEvaluationStatus.PASSED
                                : com.syndicate.regulatory.RuleEvaluationStatus.FAILED;
                        RuleEvaluation eval = new RuleEvaluation(
                                rpt.getTransaction(),
                                caRule.get(),
                                status,
                                amount.toString(),
                                "Transaction exceeds 10% turnover (" + limit + ") and is non-exempt (not ordinary course or not arm's length)."
                        );
                        eval.setEntityType("RPT");
                        eval.setEntityId(rpt.getId());
                        eval.setFormulaExpression("amount >= turnover * 0.10 && (!in_ordinary_course || !is_arms_length)");
                        ruleEvaluationRepository.save(eval);
                    }
                }
            }
        }
        rpt.setCa2013Sec188Applicable(caApplicable);

        // 2. SEBI LODR Regulation 23 Materiality Evaluation (Post-2022 Amendment)
        Optional<RegulatoryRule> lodrRule = regulatoryRuleRepository.findByCode("SEBI_LODR_REG_23_MATERIAL");
        boolean isLodrMaterial = false;
        BigDecimal lodrLimit = ONE_THOUSAND_CRORE;

        if (turnover.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = "BRAND_USAGE_OR_ROYALTY".equalsIgnoreCase(rpt.getTransactionType())
                    ? new BigDecimal("5.0")
                    : new BigDecimal("10.0");
            BigDecimal turnoverThreshold = turnover.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            lodrLimit = turnoverThreshold.min(ONE_THOUSAND_CRORE);
        }

        if (amount.compareTo(lodrLimit) >= 0) {
            isLodrMaterial = true;
            if (lodrRule.isPresent()) {
                com.syndicate.regulatory.RuleEvaluationStatus status = rpt.getShareholderApprovalDate() != null
                        ? com.syndicate.regulatory.RuleEvaluationStatus.PASSED
                        : com.syndicate.regulatory.RuleEvaluationStatus.FAILED;
                RuleEvaluation eval = new RuleEvaluation(
                        rpt.getTransaction(),
                        lodrRule.get(),
                        status,
                        amount.toString(),
                        "RPT exceeds lower of ₹1000 Cr or threshold turnover limit (" + lodrLimit + "). Prior shareholder approval mandatory."
                );
                eval.setEntityType("RPT");
                eval.setEntityId(rpt.getId());
                eval.setFormulaExpression("amount >= min(1000Cr, turnover * 0.10)");
                ruleEvaluationRepository.save(eval);
            }
        }
        rpt.setSebiLodrMaterial(isLodrMaterial);

        // Audit Committee Approval check (LODR Reg 23(2) mandates AC approval for ALL RPTs)
        if ("NONE".equalsIgnoreCase(rpt.getAuditCommitteeApproval())) {
            rpt.setShareholderApprovalRequired(caApplicable || isLodrMaterial);
        } else {
            rpt.setShareholderApprovalRequired(caApplicable || isLodrMaterial);
        }
    }
}
