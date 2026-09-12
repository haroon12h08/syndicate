package com.syndicate.invitation.dto;

import com.syndicate.transaction.TransactionRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code targetOrganizationId} is always required — it's the org this invite is for
 * (either the org the individual represents, or the org being invited generally).
 * {@code targetEmail} distinguishes the two invite kinds: set = inviting a specific
 * person from that org; null = an organization-level invite, letting that org's
 * OWNER/ADMIN pick who from their org joins after accepting.
 */
public record SendTransactionInvitationRequest(
        @NotNull UUID targetOrganizationId,
        String targetEmail,
        @NotNull TransactionRole role,
        UUID workstreamId
) {
}
