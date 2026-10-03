package com.syndicate.lifecycle.dto;

import com.syndicate.lifecycle.MilestoneType;
import com.syndicate.lifecycle.TransactionStage;
import com.syndicate.user.UserDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StageDto(TransactionStage stage, String summary, String nextAction, List<MilestoneDto> milestones,
                       List<MilestoneType> recordable) {

    public record MilestoneDto(UUID id, MilestoneType type, LocalDate occurredOn, String reference, String notes,
                               Integer documentVersion, UserDto recordedBy) {
    }
}
