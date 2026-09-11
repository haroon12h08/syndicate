package com.syndicate.transaction.dto;

import com.syndicate.transaction.TransactionRole;
import com.syndicate.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTransactionRequest(
        @NotBlank String name,
        @NotNull TransactionType type,
        @NotNull UUID leadOrganizationId,
        @NotNull TransactionRole creatorRole
) {
}
