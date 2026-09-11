package com.syndicate.transaction.dto;

import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionStatus;
import com.syndicate.transaction.TransactionType;

import java.util.UUID;

public record TransactionDto(
        UUID id,
        UUID companyId,
        String companyName,
        UUID leadOrganizationId,
        String leadOrganizationName,
        TransactionType type,
        String name,
        TransactionStatus status
) {
    public static TransactionDto from(Transaction transaction) {
        return new TransactionDto(
                transaction.getId(),
                transaction.getCompany().getId(),
                transaction.getCompany().getLegalName(),
                transaction.getLeadOrganization().getId(),
                transaction.getLeadOrganization().getName(),
                transaction.getType(),
                transaction.getName(),
                transaction.getStatus()
        );
    }
}
