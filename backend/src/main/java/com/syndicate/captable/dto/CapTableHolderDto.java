package com.syndicate.captable.dto;

import com.syndicate.captable.CapTableHolder;
import com.syndicate.captable.HolderCategory;
import java.util.UUID;

public record CapTableHolderDto(
        UUID id,
        UUID transactionId,
        UUID partyId,
        String legalName,
        HolderCategory category,
        String pan,
        String dematAccountNo,
        String residentialStatus
) {
    public static CapTableHolderDto fromEntity(CapTableHolder h) {
        return new CapTableHolderDto(
                h.getId(),
                h.getTransaction().getId(),
                h.getPartyId(),
                h.getLegalName(),
                h.getCategory(),
                h.getPan(),
                h.getDematAccountNo(),
                h.getResidentialStatus()
        );
    }
}
