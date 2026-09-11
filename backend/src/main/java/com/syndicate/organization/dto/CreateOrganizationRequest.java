package com.syndicate.organization.dto;

import com.syndicate.organization.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrganizationRequest(
        @NotBlank String name,
        @NotNull OrganizationType type
) {
}
