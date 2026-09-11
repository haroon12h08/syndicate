package com.syndicate.fact.dto;

import com.syndicate.fact.Fact;
import com.syndicate.fact.FactStatus;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FactDto(
        UUID id,
        UUID workstreamId,
        String label,
        String value,
        String unit,
        String period,
        FactStatus status,
        int version,
        UUID supersedesFactId,
        UserDto createdBy,
        UserDto verifiedBy,
        Instant verifiedAt,
        List<UUID> evidenceIds
) {
    public static FactDto from(Fact fact) {
        return new FactDto(
                fact.getId(),
                fact.getWorkstream().getId(),
                fact.getLabel(),
                fact.getValue(),
                fact.getUnit(),
                fact.getPeriod(),
                fact.getStatus(),
                fact.getVersion(),
                fact.getSupersedesFact() != null ? fact.getSupersedesFact().getId() : null,
                UserDto.from(fact.getCreatedByUser()),
                fact.getVerifiedByUser() != null ? UserDto.from(fact.getVerifiedByUser()) : null,
                fact.getVerifiedAt(),
                fact.getEvidence().stream().map(e -> e.getId()).toList()
        );
    }
}
