package com.syndicate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MfaLoginRequest(@NotBlank String mfaToken, @NotNull Integer code) {
}
