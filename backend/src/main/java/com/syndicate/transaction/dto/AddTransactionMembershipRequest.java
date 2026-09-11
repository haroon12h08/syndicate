package com.syndicate.transaction.dto;

import com.syndicate.transaction.TransactionRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddTransactionMembershipRequest(
        @NotNull UUID organizationId,
        @NotBlank @Email String email,
        @NotNull TransactionRole role
) {
}
