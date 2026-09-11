package com.syndicate.auth.dto;

import com.syndicate.organization.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
        @NotBlank String fullName,
        @NotBlank String organizationName,
        @NotNull OrganizationType organizationType
) {
}
