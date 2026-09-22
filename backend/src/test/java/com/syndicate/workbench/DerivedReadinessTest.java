package com.syndicate.workbench;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedReadinessTest {

    private static Map<BlockerSeverity, Long> counts(long blocking, long stale, long conditional, long review) {
        Map<BlockerSeverity, Long> counts = new EnumMap<>(BlockerSeverity.class);
        counts.put(BlockerSeverity.BLOCKING, blocking);
        counts.put(BlockerSeverity.STALE, stale);
        counts.put(BlockerSeverity.CONDITIONAL, conditional);
        counts.put(BlockerSeverity.AWAITING_REVIEW, review);
        return counts;
    }

    @Test
    void theStrongestBlockerDecides() {
        assertThat(WorkbenchService.derive(counts(1, 1, 1, 1))).isEqualTo(DerivedReadiness.NOT_READY);
        assertThat(WorkbenchService.derive(counts(0, 1, 1, 1))).isEqualTo(DerivedReadiness.STALE_AFTER_CHANGE);
        assertThat(WorkbenchService.derive(counts(0, 0, 1, 1))).isEqualTo(DerivedReadiness.CONDITIONALLY_READY);
        assertThat(WorkbenchService.derive(counts(0, 0, 0, 1))).isEqualTo(DerivedReadiness.READY_FOR_REVIEW);
        assertThat(WorkbenchService.derive(counts(0, 0, 0, 0))).isEqualTo(DerivedReadiness.READY_FOR_FILING);
    }
}
