package com.syndicate.lifecycle.dto;

import com.syndicate.lifecycle.TransactionMilestone;
import com.syndicate.user.UserDto;

public final class MilestoneDtoMapper {

    private MilestoneDtoMapper() {
    }

    public static StageDto.MilestoneDto toDto(TransactionMilestone milestone) {
        return new StageDto.MilestoneDto(milestone.getId(), milestone.getType(), milestone.getOccurredOn(),
                milestone.getReference(), milestone.getNotes(),
                milestone.getDocument() != null ? milestone.getDocument().getVersion() : null,
                UserDto.from(milestone.getRecordedBy()));
    }
}
