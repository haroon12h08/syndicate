package com.syndicate.captable;

import com.syndicate.captable.dto.CapTableSummaryDto;
import com.syndicate.captable.dto.HolderShareholdingDto;
import com.syndicate.regulatory.RegulatoryRule;
import com.syndicate.regulatory.RegulatoryRuleRepository;
import com.syndicate.regulatory.RuleEvaluation;
import com.syndicate.regulatory.RuleEvaluationRepository;
import com.syndicate.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
public class LockInCalculator {

    private static final BigDecimal TWENTY_PCT = new BigDecimal("0.20");
    private static final BigDecimal HALF = new BigDecimal("0.50");

    private final RegulatoryRuleRepository regulatoryRuleRepository;
    private final RuleEvaluationRepository ruleEvaluationRepository;

    public LockInCalculator(RegulatoryRuleRepository regulatoryRuleRepository,
                            RuleEvaluationRepository ruleEvaluationRepository) {
        this.regulatoryRuleRepository = regulatoryRuleRepository;
        this.ruleEvaluationRepository = ruleEvaluationRepository;
    }

    /**
     * Calculates the deterministic statutory lock-in schedule based on SEBI ICDR 2025 amendment
     * (Regulation 238) and Pre-Issue Non-Promoter Capital (Regulation 239).
     */
    public List<ShareLockIn> calculateLockIns(
            Transaction transaction,
            CapTableSummaryDto summary,
            List<CapTableHolder> holders,
            List<SecurityClass> securityClasses,
            LocalDate allotmentDate,
            Map<UUID, LockInExemption> nonPromoterExemptions) {

        if (allotmentDate == null) {
            allotmentDate = LocalDate.now();
        }

        SecurityClass primaryEquity = securityClasses.stream()
                .filter(sc -> sc.getSecurityType() == SecurityType.EQUITY)
                .findFirst()
                .orElse(securityClasses.isEmpty() ? null : securityClasses.get(0));

        if (primaryEquity == null) {
            return Collections.emptyList();
        }

        Map<UUID, CapTableHolder> holderMap = new HashMap<>();
        for (CapTableHolder h : holders) {
            holderMap.put(h.getId(), h);
        }

        List<ShareLockIn> generatedLockIns = new ArrayList<>();

        Optional<RegulatoryRule> reg238Opt = regulatoryRuleRepository.findByCode("SEBI_ICDR_REG_238_2025");
        Optional<RegulatoryRule> reg239Opt = regulatoryRuleRepository.findByCode("SEBI_ICDR_REG_239");

        UUID reg238Id = reg238Opt.map(RegulatoryRule::getId).orElse(null);
        UUID reg239Id = reg239Opt.map(RegulatoryRule::getId).orElse(null);

        BigDecimal postIssueShares = summary.postIssueShares();
        BigDecimal totalMpcRequired = postIssueShares.multiply(TWENTY_PCT).setScale(0, RoundingMode.CEILING);
        BigDecimal promoterPostIssueShares = summary.promoterPostIssueShares();

        // 1. Evaluate Reg 238 MPC
        com.syndicate.regulatory.RuleEvaluationStatus mpcStatus = summary.isMpcSatisfied()
                ? com.syndicate.regulatory.RuleEvaluationStatus.PASSED
                : com.syndicate.regulatory.RuleEvaluationStatus.FAILED;
        String mpcDetails = String.format(
                "Promoter Holding: %s shares (%.2f%%). MPC Required (20%% of %s): %s shares. Shortfall: %s shares.",
                promoterPostIssueShares, summary.promoterPostIssuePct(), postIssueShares,
                totalMpcRequired, summary.mpcShortfallShares()
        );

        if (reg238Opt.isPresent()) {
            RuleEvaluation eval238 = new RuleEvaluation(
                    transaction,
                    reg238Opt.get(),
                    mpcStatus,
                    summary.promoterPostIssuePct().toString(),
                    mpcDetails
            );
            eval238.setEntityType("CAP_TABLE");
            eval238.setEntityId(transaction.getId());
            eval238.setFormulaExpression("promoter_post_issue_shares >= 0.20 * post_issue_shares");
            ruleEvaluationRepository.save(eval238);
        }

        // Allocate MPC and Excess among Promoters
        List<HolderShareholdingDto> promoterHoldings = summary.holdings().stream()
                .filter(h -> h.category() == HolderCategory.PROMOTER || h.category() == HolderCategory.PROMOTER_GROUP)
                .filter(h -> h.postIssueShares().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal mpcRemainingToAllocate = totalMpcRequired;

        for (HolderShareholdingDto ph : promoterHoldings) {
            CapTableHolder holder = holderMap.get(ph.holderId());
            if (holder == null) continue;

            BigDecimal holderShares = ph.postIssueShares();
            BigDecimal holderMpc = BigDecimal.ZERO;

            if (mpcRemainingToAllocate.compareTo(BigDecimal.ZERO) > 0) {
                holderMpc = holderShares.min(mpcRemainingToAllocate);
                mpcRemainingToAllocate = mpcRemainingToAllocate.subtract(holderMpc);

                // Tier 1: MPC locked for 3 years
                generatedLockIns.add(new ShareLockIn(
                        transaction,
                        holder,
                        primaryEquity,
                        holderMpc,
                        LockInTier.PROMOTER_MINIMUM_CONTRIBUTION_3YR,
                        reg238Id,
                        allotmentDate,
                        allotmentDate.plusYears(3),
                        true,
                        LockInExemption.NONE,
                        null
                ));
            }

            // Excess over MPC: 50% for 2 years, 50% for 1 year (2025 SEBI ICDR Amendment)
            BigDecimal excess = holderShares.subtract(holderMpc);
            if (excess.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tier2yr = excess.multiply(HALF).setScale(0, RoundingMode.HALF_UP);
                BigDecimal tier1yr = excess.subtract(tier2yr);

                if (tier2yr.compareTo(BigDecimal.ZERO) > 0) {
                    generatedLockIns.add(new ShareLockIn(
                            transaction,
                            holder,
                            primaryEquity,
                            tier2yr,
                            LockInTier.PROMOTER_EXCESS_2YR,
                            reg238Id,
                            allotmentDate,
                            allotmentDate.plusYears(2),
                            true,
                            LockInExemption.NONE,
                            null
                    ));
                }

                if (tier1yr.compareTo(BigDecimal.ZERO) > 0) {
                    generatedLockIns.add(new ShareLockIn(
                            transaction,
                            holder,
                            primaryEquity,
                            tier1yr,
                            LockInTier.PROMOTER_EXCESS_1YR,
                            reg238Id,
                            allotmentDate,
                            allotmentDate.plusYears(1),
                            true,
                            LockInExemption.NONE,
                            null
                    ));
                }
            }
        }

        // 2. Evaluate Reg 239: Non-promoter pre-issue capital locked for 1 year
        List<HolderShareholdingDto> nonPromoterHoldings = summary.holdings().stream()
                .filter(h -> h.category() != HolderCategory.PROMOTER && h.category() != HolderCategory.PROMOTER_GROUP)
                .filter(h -> h.basicShares().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        for (HolderShareholdingDto nph : nonPromoterHoldings) {
            CapTableHolder holder = holderMap.get(nph.holderId());
            if (holder == null) continue;

            LockInExemption exemption = nonPromoterExemptions != null
                    ? nonPromoterExemptions.getOrDefault(holder.getId(), LockInExemption.NONE)
                    : LockInExemption.NONE;

            // If holder is ESOP_TRUST or category is AIF_FVCI, auto-detect statutory exemption if not specified
            if (exemption == LockInExemption.NONE) {
                if (holder.getCategory() == HolderCategory.ESOP_TRUST) {
                    exemption = LockInExemption.ESOP_EXEMPTION;
                } else if (holder.getCategory() == HolderCategory.AIF_FVCI) {
                    exemption = LockInExemption.AIF_VCF_LOOKBACK_EXEMPTION;
                }
            }

            if (exemption == LockInExemption.NONE) {
                generatedLockIns.add(new ShareLockIn(
                        transaction,
                        holder,
                        primaryEquity,
                        nph.basicShares(),
                        LockInTier.PRE_ISSUE_NON_PROMOTER_1YR,
                        reg239Id,
                        allotmentDate,
                        allotmentDate.plusYears(1),
                        true,
                        LockInExemption.NONE,
                        null
                ));
            } else {
                // Record exemption record with 0 or exempt note
                generatedLockIns.add(new ShareLockIn(
                        transaction,
                        holder,
                        primaryEquity,
                        nph.basicShares(),
                        LockInTier.PRE_ISSUE_NON_PROMOTER_1YR,
                        reg239Id,
                        allotmentDate,
                        allotmentDate,
                        false,
                        exemption,
                        null
                ));
            }
        }

        return generatedLockIns;
    }
}
