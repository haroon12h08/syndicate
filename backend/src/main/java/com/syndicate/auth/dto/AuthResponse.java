package com.syndicate.auth.dto;

import com.syndicate.organization.dto.OrganizationDto;
import com.syndicate.user.UserDto;

public record AuthResponse(String token, UserDto user, OrganizationDto organization) {
    public static AuthResponse withoutOrganization(String token, UserDto user) {
        return new AuthResponse(token, user, null);
    }
}
