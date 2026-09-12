package com.syndicate.drhp.dto;

import java.util.List;
import java.util.UUID;

public record CompiledSection(
        UUID disclosureId,
        String sectionCode,
        String title,
        List<CompiledSegment> segments
) {
}
