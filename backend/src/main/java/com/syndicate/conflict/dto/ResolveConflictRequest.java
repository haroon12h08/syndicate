package com.syndicate.conflict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ResolveConflictRequest(@NotNull UUID chosenFactId, @NotBlank String reason,
                                     List<UUID> evidenceConsideredIds) {
}
