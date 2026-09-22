package com.syndicate.workbench;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkbenchDto(DerivedReadiness readiness, String explanation, Map<BlockerSeverity, Long> counts,
                           List<BlockerDto> blockers, CoverageDto coverage, Instant evaluatedAt) {
}
