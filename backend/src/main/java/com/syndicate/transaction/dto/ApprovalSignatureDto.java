package com.syndicate.transaction.dto;

import com.syndicate.transaction.TransactionApprovalSignature;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalSignatureDto(
        UUID id,
        String transition,
        TransactionRole requiredRole,
        UserDto signedBy,
        Instant signedAt,
        String comment
) {
    public static ApprovalSignatureDto from(TransactionApprovalSignature signature) {
        return new ApprovalSignatureDto(
                signature.getId(),
                signature.getTransition(),
                signature.getRequiredRole(),
                UserDto.from(signature.getSignedByUser()),
                signature.getSignedAt(),
                signature.getComment()
        );
    }
}
