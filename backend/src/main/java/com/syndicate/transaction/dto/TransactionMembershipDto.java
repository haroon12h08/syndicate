package com.syndicate.transaction.dto;

import com.syndicate.transaction.TransactionMembership;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.UserDto;

import java.util.UUID;

public record TransactionMembershipDto(
        UUID id,
        UUID transactionId,
        UUID organizationId,
        String organizationName,
        UserDto user,
        TransactionRole role
) {
    public static TransactionMembershipDto from(TransactionMembership membership) {
        return new TransactionMembershipDto(
                membership.getId(),
                membership.getTransaction().getId(),
                membership.getOrganization().getId(),
                membership.getOrganization().getName(),
                UserDto.from(membership.getUser()),
                membership.getRole()
        );
    }
}
