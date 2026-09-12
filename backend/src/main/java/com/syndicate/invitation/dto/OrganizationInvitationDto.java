package com.syndicate.invitation.dto;

import com.syndicate.invitation.InvitationStatus;
import com.syndicate.invitation.OrganizationInvitation;
import com.syndicate.organization.OrgRole;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record OrganizationInvitationDto(
        UUID id,
        UUID organizationId,
        String organizationName,
        UserDto inviter,
        String inviteeEmail,
        OrgRole role,
        String token,
        InvitationStatus status,
        Instant expiresAt
) {
    public static OrganizationInvitationDto from(OrganizationInvitation invitation) {
        return new OrganizationInvitationDto(
                invitation.getId(),
                invitation.getOrganization().getId(),
                invitation.getOrganization().getName(),
                UserDto.from(invitation.getInviterUser()),
                invitation.getInviteeEmail(),
                invitation.getRole(),
                invitation.getToken(),
                invitation.getStatus(),
                invitation.getExpiresAt()
        );
    }
}
