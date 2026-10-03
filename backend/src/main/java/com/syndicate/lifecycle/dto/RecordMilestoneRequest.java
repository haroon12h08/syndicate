package com.syndicate.lifecycle.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordMilestoneRequest(@NotNull LocalDate occurredOn, String reference, String notes) {
}
