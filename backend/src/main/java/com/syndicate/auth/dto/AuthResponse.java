package com.syndicate.auth.dto;

import com.syndicate.organization.dto.OrganizationDto;
import com.syndicate.user.UserDto;

public record AuthResponse(
        String token,
        UserDto user,
        OrganizationDto organization,
        boolean mfaRequired,
        String mfaToken) {

    public AuthResponse(String token, UserDto user, OrganizationDto organization) {
        this(token, user, organization, false, null);
    }

    public static AuthResponse withoutOrganization(String token, UserDto user) {
        return new AuthResponse(token, user, null, false, null);
    }

    public static AuthResponse mfaRequired(String mfaToken) {
        return new AuthResponse(null, null, null, true, mfaToken);
    }
}
