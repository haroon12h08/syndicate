package com.syndicate.drhp.dto;

import java.util.List;
import java.util.UUID;

/**
 * One piece of a rendered section: either literal prose, or a value traced back to the fact it
 * came from so a reader can follow it to its evidence.
 */
public record CompiledSegment(
        String type,
        String text,
        UUID factId,
        String label,
        String value,
        String unit,
        List<UUID> evidenceIds
) {
    public static CompiledSegment text(String text) {
        return new CompiledSegment("TEXT", text, null, null, null, null, List.of());
    }

    public static CompiledSegment fact(UUID factId, String label, String value, String unit,
                                        List<UUID> evidenceIds) {
        return new CompiledSegment("FACT", null, factId, label, value, unit, evidenceIds);
    }

    /** Kept in the output so the reader can see exactly where the gap is. */
    public static CompiledSegment unresolved(String label) {
        return new CompiledSegment("UNRESOLVED", null, null, label, null, null, List.of());
    }
}
