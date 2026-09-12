package com.syndicate.invitation.dto;

import com.syndicate.invitation.InvitationStatus;
import com.syndicate.invitation.TransactionInvitation;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record TransactionInvitationDto(
        UUID id,
        UUID transactionId,
        String transactionName,
        UserDto inviter,
        String inviteeEmail,
        boolean organizationInvite,
        UUID organizationId,
        String organizationName,
        UUID workstreamId,
        TransactionRole role,
        String token,
        InvitationStatus status,
        Instant expiresAt
) {
    public static TransactionInvitationDto from(TransactionInvitation invitation) {
        return new TransactionInvitationDto(
                invitation.getId(),
                invitation.getTransaction().getId(),
                invitation.getTransaction().getName(),
                UserDto.from(invitation.getInviterUser()),
                invitation.getInviteeEmail(),
                invitation.isOrganizationInvite(),
                invitation.getOrganization().getId(),
                invitation.getOrganization().getName(),
                invitation.getWorkstream() != null ? invitation.getWorkstream().getId() : null,
                invitation.getRole(),
                invitation.getToken(),
                invitation.getStatus(),
                invitation.getExpiresAt()
        );
    }
}
