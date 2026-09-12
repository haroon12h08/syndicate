package com.syndicate.invitation.dto;

import com.syndicate.invitation.InvitationStatus;

import java.time.Instant;

/** Public preview shown on the accept-invitation landing page, before login. */
public record InvitationPreviewDto(
        String kind,
        String organizationName,
        String transactionName,
        String workstreamLabel,
        String role,
        String inviterName,
        InvitationStatus status,
        Instant expiresAt,
        boolean expired
) {
}
