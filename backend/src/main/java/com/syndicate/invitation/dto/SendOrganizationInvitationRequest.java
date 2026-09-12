package com.syndicate.invitation.dto;

import com.syndicate.organization.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendOrganizationInvitationRequest(
        @NotBlank @Email String email,
        @NotNull OrgRole role
) {
}
