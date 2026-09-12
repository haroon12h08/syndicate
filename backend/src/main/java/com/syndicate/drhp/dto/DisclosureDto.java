package com.syndicate.drhp.dto;

import com.syndicate.drhp.Disclosure;
import com.syndicate.drhp.DisclosureStatus;

import java.util.List;
import java.util.UUID;

public record DisclosureDto(
        UUID id,
        UUID transactionId,
        String sectionCode,
        String title,
        String bodyTemplate,
        DisclosureStatus status,
        int orderIndex,
        String staleReason,
        List<UUID> sourceFactIds
) {
    public static DisclosureDto from(Disclosure d) {
        return new DisclosureDto(d.getId(), d.getTransaction().getId(), d.getSectionCode(), d.getTitle(),
                d.getBodyTemplate(), d.getStatus(), d.getOrderIndex(), d.getStaleReason(),
                d.getSourceFacts().stream().map(f -> f.getId()).toList());
    }
}
