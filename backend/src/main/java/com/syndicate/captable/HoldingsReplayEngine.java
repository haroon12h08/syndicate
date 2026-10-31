package com.syndicate.captable;

import com.syndicate.captable.dto.CapTableSummaryDto;
import com.syndicate.captable.dto.HolderShareholdingDto;
import com.syndicate.captable.dto.IpoIssueParametersDto;
import com.syndicate.captable.dto.ShareEncumbranceDto;
import com.syndicate.captable.dto.ShareLockInDto;
import com.syndicate.captable.dto.ShareTransactionDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Component
public class HoldingsReplayEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWENTY_PCT = new BigDecimal("0.20");

    public CapTableSummaryDto replay(
            LocalDate asOfDate,
            List<SecurityClass> securityClasses,
            List<CapTableHolder> holders,
            List<ShareTransaction> transactions,
            List<ShareLockIn> lockIns,
            List<ShareEncumbrance> encumbrances,
            IpoIssueParameters ipoParameters) {

        Map<UUID, SecurityClass> secMap = new HashMap<>();
        for (SecurityClass sc : securityClasses) {
            secMap.put(sc.getId(), sc);
        }

        Map<UUID, CapTableHolder> holderMap = new HashMap<>();
        for (CapTableHolder h : holders) {
            holderMap.put(h.getId(), h);
        }

        // Map: holderId -> (securityClassId -> shareCount)
        Map<UUID, Map<UUID, BigDecimal>> balances = new HashMap<>();
        for (CapTableHolder h : holders) {
            balances.put(h.getId(), new HashMap<>());
        }

        // Filter and sort transactions chronologically
        List<ShareTransaction> sortedTx = transactions.stream()
                .filter(t -> asOfDate == null || !t.getEventDate().isAfter(asOfDate))
                .sorted(Comparator.comparing(ShareTransaction::getEventDate)
                        .thenComparing(ShareTransaction::getCreatedAt))
                .toList();

        for (ShareTransaction st : sortedTx) {
            BigDecimal qty = st.getShareCount();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Share transaction " + st.getId() + " count must be positive");
            }
            UUID scId = st.getSecurityClass().getId();

            if (st.getSourceHolder() != null) {
                UUID srcId = st.getSourceHolder().getId();
                Map<UUID, BigDecimal> srcBalances = balances.computeIfAbsent(srcId, k -> new HashMap<>());
                BigDecimal current = srcBalances.getOrDefault(scId, BigDecimal.ZERO);
                if (current.compareTo(qty) < 0) {
                    throw new IllegalStateException("Holder " + st.getSourceHolder().getLegalName()
                            + " has insufficient shares (" + current + ") for event " + st.getEventType()
                            + " of " + qty + " shares");
                }
                srcBalances.put(scId, current.subtract(qty));
            }

            if (st.getDestHolder() != null) {
                UUID destId = st.getDestHolder().getId();
                Map<UUID, BigDecimal> destBalances = balances.computeIfAbsent(destId, k -> new HashMap<>());
                BigDecimal current = destBalances.getOrDefault(scId, BigDecimal.ZERO);
                destBalances.put(scId, current.add(qty));
            }
        }

        // Encumbrance and LockIn maps by holder
        Map<UUID, BigDecimal> lockedByHolder = new HashMap<>();
        for (ShareLockIn li : lockIns) {
            lockedByHolder.merge(li.getHolder().getId(), li.getLockedShareCount(), BigDecimal::add);
        }

        Map<UUID, BigDecimal> encumberedByHolder = new HashMap<>();
        for (ShareEncumbrance se : encumbrances) {
            if ("ACTIVE".equalsIgnoreCase(se.getStatus())) {
                encumberedByHolder.merge(se.getHolder().getId(), se.getEncumberedShares(), BigDecimal::add);
            }
        }

        // Calculate holder-level basic and diluted holdings
        BigDecimal totalBasicShares = BigDecimal.ZERO;
        BigDecimal totalDilutedShares = BigDecimal.ZERO;
        BigDecimal preIssuePaidUpCapital = BigDecimal.ZERO;

        BigDecimal defaultFaceValue = new BigDecimal("10.00");
        for (SecurityClass sc : securityClasses) {
            if (sc.getSecurityType() == SecurityType.EQUITY && sc.getFaceValue() != null) {
                defaultFaceValue = sc.getFaceValue();
                break;
            }
        }

        Map<UUID, BigDecimal> holderBasic = new HashMap<>();
        Map<UUID, BigDecimal> holderDiluted = new HashMap<>();

        for (CapTableHolder h : holders) {
            Map<UUID, BigDecimal> holderBals = balances.getOrDefault(h.getId(), Collections.emptyMap());
            BigDecimal basic = BigDecimal.ZERO;
            BigDecimal diluted = BigDecimal.ZERO;

            for (Map.Entry<UUID, BigDecimal> entry : holderBals.entrySet()) {
                BigDecimal bal = entry.getValue();
                if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
                SecurityClass sc = secMap.get(entry.getKey());
                if (sc == null) continue;

                if (sc.getSecurityType() == SecurityType.EQUITY) {
                    basic = basic.add(bal);
                    diluted = diluted.add(bal);
                    BigDecimal fv = sc.getFaceValue() != null ? sc.getFaceValue() : defaultFaceValue;
                    preIssuePaidUpCapital = preIssuePaidUpCapital.add(bal.multiply(fv));
                } else if (sc.isConvertible()) {
                    BigDecimal ratio = sc.getConversionRatio() != null ? sc.getConversionRatio() : BigDecimal.ONE;
                    BigDecimal converted = bal.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
                    diluted = diluted.add(converted);
                } else if (sc.getSecurityType() == SecurityType.ESOP_POOL) {
                    diluted = diluted.add(bal);
                }
            }

            holderBasic.put(h.getId(), basic);
            holderDiluted.put(h.getId(), diluted);
            totalBasicShares = totalBasicShares.add(basic);
            totalDilutedShares = totalDilutedShares.add(diluted);
        }

        BigDecimal freshIssueShares = ipoParameters != null ? ipoParameters.getFreshIssueShares() : BigDecimal.ZERO;
        BigDecimal ofsShares = ipoParameters != null ? ipoParameters.getOfsShares() : BigDecimal.ZERO;
        BigDecimal postIssueShares = totalBasicShares.add(freshIssueShares);
        BigDecimal postIssuePaidUpCapital = postIssueShares.multiply(defaultFaceValue);

        BigDecimal promoterBasicShares = BigDecimal.ZERO;
        BigDecimal promoterDilutedShares = BigDecimal.ZERO;

        for (CapTableHolder h : holders) {
            if (h.getCategory() == HolderCategory.PROMOTER || h.getCategory() == HolderCategory.PROMOTER_GROUP) {
                promoterBasicShares = promoterBasicShares.add(holderBasic.getOrDefault(h.getId(), BigDecimal.ZERO));
                promoterDilutedShares = promoterDilutedShares.add(holderDiluted.getOrDefault(h.getId(), BigDecimal.ZERO));
            }
        }

        BigDecimal promoterBasicPct = totalBasicShares.compareTo(BigDecimal.ZERO) > 0
                ? promoterBasicShares.multiply(HUNDRED).divide(totalBasicShares, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Promoter post-issue: OFS is subtracted from selling promoter holdings if specified
        // Otherwise default post-issue promoter shares = promoterBasicShares - ofsShares
        BigDecimal promoterPostIssueShares = promoterBasicShares.compareTo(ofsShares) >= 0
                ? promoterBasicShares.subtract(ofsShares)
                : BigDecimal.ZERO;

        BigDecimal promoterPostIssuePct = postIssueShares.compareTo(BigDecimal.ZERO) > 0
                ? promoterPostIssueShares.multiply(HUNDRED).divide(postIssueShares, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal mpcRequiredShares = postIssueShares.multiply(TWENTY_PCT).setScale(0, RoundingMode.CEILING);
        BigDecimal mpcShortfallShares = mpcRequiredShares.subtract(promoterPostIssueShares);
        if (mpcShortfallShares.compareTo(BigDecimal.ZERO) < 0) {
            mpcShortfallShares = BigDecimal.ZERO;
        }
        boolean isMpcSatisfied = promoterPostIssueShares.compareTo(mpcRequiredShares) >= 0;

        List<HolderShareholdingDto> holdingsDtoList = new ArrayList<>();
        for (CapTableHolder h : holders) {
            BigDecimal bShares = holderBasic.getOrDefault(h.getId(), BigDecimal.ZERO);
            BigDecimal dShares = holderDiluted.getOrDefault(h.getId(), BigDecimal.ZERO);

            BigDecimal bPct = totalBasicShares.compareTo(BigDecimal.ZERO) > 0
                    ? bShares.multiply(HUNDRED).divide(totalBasicShares, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal dPct = totalDilutedShares.compareTo(BigDecimal.ZERO) > 0
                    ? dShares.multiply(HUNDRED).divide(totalDilutedShares, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal pShares = bShares;
            if (h.getCategory() == HolderCategory.PROMOTER || h.getCategory() == HolderCategory.PROMOTER_GROUP) {
                // If ofs is applied proportionally or to primary promoter
                if (promoterBasicShares.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal holderOfsShare = ofsShares.multiply(bShares).divide(promoterBasicShares, 0, RoundingMode.HALF_UP);
                    pShares = bShares.compareTo(holderOfsShare) >= 0 ? bShares.subtract(holderOfsShare) : BigDecimal.ZERO;
                }
            }

            BigDecimal pPct = postIssueShares.compareTo(BigDecimal.ZERO) > 0
                    ? pShares.multiply(HUNDRED).divide(postIssueShares, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            holdingsDtoList.add(new HolderShareholdingDto(
                    h.getId(),
                    h.getLegalName(),
                    h.getCategory(),
                    bShares,
                    bPct,
                    dShares,
                    dPct,
                    pShares,
                    pPct,
                    lockedByHolder.getOrDefault(h.getId(), BigDecimal.ZERO),
                    encumberedByHolder.getOrDefault(h.getId(), BigDecimal.ZERO)
            ));
        }

        // Sort holdings by basic share count descending
        holdingsDtoList.sort(Comparator.comparing(HolderShareholdingDto::basicShares).reversed());

        return new CapTableSummaryDto(
                asOfDate != null ? asOfDate : LocalDate.now(),
                totalBasicShares,
                totalDilutedShares,
                preIssuePaidUpCapital,
                freshIssueShares,
                ofsShares,
                postIssueShares,
                postIssuePaidUpCapital,
                promoterBasicShares,
                promoterBasicPct,
                promoterPostIssueShares,
                promoterPostIssuePct,
                mpcRequiredShares,
                mpcShortfallShares,
                isMpcSatisfied,
                holdingsDtoList,
                lockIns.stream().map(ShareLockInDto::fromEntity).toList(),
                encumbrances.stream().map(ShareEncumbranceDto::fromEntity).toList(),
                sortedTx.stream().map(ShareTransactionDto::fromEntity).toList(),
                IpoIssueParametersDto.fromEntity(ipoParameters),
                Collections.emptyList()
        );
    }
}
