package com.syndicate.transaction.dto;

import com.syndicate.transaction.TransactionRole;

import java.util.List;
import java.util.Set;

public record ApprovalStatusDto(
        String transition,
        Set<TransactionRole> requiredRoles,
        List<ApprovalSignatureDto> signatures,
        boolean satisfied
) {
}
