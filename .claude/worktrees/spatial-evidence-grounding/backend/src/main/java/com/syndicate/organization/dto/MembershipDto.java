package com.syndicate.organization.dto;

import com.syndicate.organization.OrgRole;
import com.syndicate.organization.OrganizationMembership;
import com.syndicate.user.UserDto;

import java.util.UUID;

public record MembershipDto(UUID id, UUID organizationId, UserDto user, OrgRole role) {
    public static MembershipDto from(OrganizationMembership membership) {
        return new MembershipDto(
                membership.getId(),
                membership.getOrganization().getId(),
                UserDto.from(membership.getUser()),
                membership.getRole()
        );
    }
}
