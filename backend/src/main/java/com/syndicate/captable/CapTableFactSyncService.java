package com.syndicate.captable;

import com.syndicate.captable.dto.CapTableConflictDto;
import com.syndicate.captable.dto.CapTableSummaryDto;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CapTableFactSyncService {

    private static final Logger log = LoggerFactory.getLogger(CapTableFactSyncService.class);
    private final FactRepository factRepository;

    public CapTableFactSyncService(FactRepository factRepository) {
        this.factRepository = factRepository;
    }

    public List<CapTableConflictDto> checkFactSync(UUID transactionId, CapTableSummaryDto summary) {
        List<Fact> facts = factRepository.findByWorkstreamTransactionIdAndStatus(transactionId, FactStatus.VERIFIED);
        if (facts.isEmpty()) {
            facts = factRepository.findByWorkstreamTransactionId(transactionId);
        }

        Map<String, BigDecimal> expectedValues = new HashMap<>();
        expectedValues.put("issuer.share_capital", summary.preIssuePaidUpCapitalInr());
        expectedValues.put("issuer.share_count", summary.totalBasicShares());
        expectedValues.put("issuer.promoter_ownership_pct", summary.promoterBasicPct());
        expectedValues.put("issue.post_issue_paid_up_capital", summary.postIssuePaidUpCapitalInr());
        expectedValues.put("issue.fresh_issue_shares", summary.freshIssueShares());
        expectedValues.put("issue.ofs_shares", summary.ofsShares());

        List<CapTableConflictDto> conflicts = new ArrayList<>();

        for (Fact f : facts) {
            String key = f.getFactKey();
            if (expectedValues.containsKey(key)) {
                BigDecimal expected = expectedValues.get(key);
                try {
                    BigDecimal actual = new BigDecimal(f.getValue().trim().replaceAll("[^0-9.]", ""));
                    BigDecimal diff = actual.subtract(expected).abs();
                    // Tolerance check
                    if (diff.compareTo(new BigDecimal("0.50")) > 0) {
                        conflicts.add(new CapTableConflictDto(
                                key,
                                "Cap table engine computed " + expected + ", but verified fact has " + actual,
                                expected,
                                actual,
                                "DIVERGENCE_DETECTED"
                        ));
                    }
                } catch (Exception ex) {
                    log.warn("Could not parse fact value for key {}: {}", key, f.getValue());
                }
            }
        }

        return conflicts;
    }
}
