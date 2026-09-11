package com.syndicate.transaction.dto;

import com.syndicate.transaction.TransactionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTransactionRequest(@NotNull TransactionStatus status) {
}
