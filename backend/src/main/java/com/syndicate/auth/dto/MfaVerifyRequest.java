package com.syndicate.auth.dto;

import jakarta.validation.constraints.NotNull;

public record MfaVerifyRequest(@NotNull Integer code) {
}
