package com.syndicate.rpt.dto;

import com.syndicate.rpt.RelatedParty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RelatedPartyDto(
        UUID id,
        UUID transactionId,
        String name,
        String partyType,
        String pan,
        String dinCin,
        String relationshipNature,
        String relativeDetails,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean isSuggested,
        UUID confirmedByUserId,
        Instant confirmedAt
) {
    public static RelatedPartyDto fromEntity(RelatedParty rp) {
        return new RelatedPartyDto(
                rp.getId(),
                rp.getTransaction().getId(),
                rp.getName(),
                rp.getPartyType(),
                rp.getPan(),
                rp.getDinCin(),
                rp.getRelationshipNature(),
                rp.getRelativeDetails(),
                rp.getEffectiveFrom(),
                rp.getEffectiveTo(),
                rp.isSuggested(),
                rp.getConfirmedByUser() != null ? rp.getConfirmedByUser().getId() : null,
                rp.getConfirmedAt()
        );
    }
}
