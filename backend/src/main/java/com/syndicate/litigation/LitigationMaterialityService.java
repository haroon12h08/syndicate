package com.syndicate.litigation;

import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class LitigationMaterialityService {

    private final FactRepository factRepository;

    public LitigationMaterialityService(FactRepository factRepository) {
        this.factRepository = factRepository;
    }

    public void evaluateMateriality(LitigationCase lc) {
        String cat = lc.getCategory() != null ? lc.getCategory().toUpperCase() : "";

        // 1. Mandatory statutory categories under SEBI ICDR Schedule VI
        if ("CRIMINAL_PROCEEDING".equals(cat)) {
            lc.setMaterial(true);
            lc.setMaterialityBasis("CRIMINAL_MANDATORY");
            return;
        }

        if ("REGULATORY_STATUTORY_ACTION".equals(cat) || "EXCHANGE_DISCIPLINARY_ACTION".equals(cat)) {
            lc.setMaterial(true);
            lc.setMaterialityBasis("REGULATORY_MANDATORY");
            return;
        }

        // 2. Qualitative operational risk
        String impact = lc.getPotentialImpact() != null ? lc.getPotentialImpact().toUpperCase() : "";
        if ("MANAGEMENT_DISQUALIFICATION".equals(impact) || "PERMIT_REVOCATION".equals(impact) || "OPERATIONAL".equals(impact)) {
            lc.setMaterial(true);
            lc.setMaterialityBasis("QUALITATIVE_OPERATION");
            return;
        }

        // 3. Quantitative financial thresholds (% net worth or % turnover)
        BigDecimal exposure = lc.getClaimedAmount() != null ? lc.getClaimedAmount() : BigDecimal.ZERO;
        if (exposure.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal netWorth = BigDecimal.ZERO;
            BigDecimal turnover = BigDecimal.ZERO;

            List<Fact> facts = factRepository.findByWorkstreamTransactionId(lc.getTransaction().getId());
            for (Fact f : facts) {
                if ("issuer.net_worth".equalsIgnoreCase(f.getFactKey())) {
                    try {
                        netWorth = new BigDecimal(f.getValue().trim().replaceAll("[^0-9.]", ""));
                    } catch (Exception ignored) {}
                } else if ("issuer.revenue".equalsIgnoreCase(f.getFactKey()) || "issuer.turnover".equalsIgnoreCase(f.getFactKey())) {
                    try {
                        turnover = new BigDecimal(f.getValue().trim().replaceAll("[^0-9.]", ""));
                    } catch (Exception ignored) {}
                }
            }

            // Material if >= 5% of Net Worth
            if (netWorth.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal nwLimit = netWorth.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
                if (exposure.compareTo(nwLimit) >= 0) {
                    lc.setMaterial(true);
                    lc.setMaterialityBasis("NET_WORTH_THRESHOLD");
                    return;
                }
            }

            // Material if >= 2% of Turnover
            if (turnover.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal toLimit = turnover.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
                if (exposure.compareTo(toLimit) >= 0) {
                    lc.setMaterial(true);
                    lc.setMaterialityBasis("TURNOVER_THRESHOLD");
                    return;
                }
            }
        }

        // If explicitly insolvency/bankruptcy
        if ("INSOLVENCY_BANKRUPTCY".equals(cat)) {
            lc.setMaterial(true);
            lc.setMaterialityBasis("INSOLVENCY_MANDATORY");
            return;
        }

        // Non-material otherwise
        lc.setMaterial(false);
        lc.setMaterialityBasis("BELOW_THRESHOLDS");
    }
}
