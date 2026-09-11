package com.syndicate.workstream.dto;

import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamType;

import java.util.UUID;

public record WorkstreamDto(UUID id, UUID transactionId, WorkstreamType type, String description) {
    public static WorkstreamDto from(Workstream workstream) {
        return new WorkstreamDto(
                workstream.getId(),
                workstream.getTransaction().getId(),
                workstream.getType(),
                workstream.getDescription()
        );
    }
}
