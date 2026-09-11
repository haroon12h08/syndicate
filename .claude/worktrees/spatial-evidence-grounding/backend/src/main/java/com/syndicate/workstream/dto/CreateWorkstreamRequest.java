package com.syndicate.workstream.dto;

import com.syndicate.workstream.WorkstreamType;
import jakarta.validation.constraints.NotNull;

public record CreateWorkstreamRequest(@NotNull WorkstreamType type, String description) {
}
