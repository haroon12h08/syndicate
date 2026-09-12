package com.syndicate.transaction.dto;

import jakarta.validation.constraints.NotBlank;

public record SignApprovalRequest(
        @NotBlank String transition,
        String comment
) {
}
