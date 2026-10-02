package com.syndicate.filing.dto;

import com.syndicate.filing.DocumentApproval;
import com.syndicate.transaction.TransactionRole;
import com.syndicate.user.UserDto;

import java.time.Instant;
import java.util.UUID;

public record ApprovalDto(UUID id, UUID documentId, int documentVersion, UserDto approver, TransactionRole role,
                          Instant grantedAt, Instant invalidatedAt, String invalidationReason, boolean current) {

    public static ApprovalDto from(DocumentApproval approval) {
        return new ApprovalDto(approval.getId(), approval.getDocument().getId(),
                approval.getDocument().getVersion(), UserDto.from(approval.getApprover()),
                approval.getApproverRole(), approval.getGrantedAt(), approval.getInvalidatedAt(),
                approval.getInvalidationReason(), approval.isCurrent());
    }
}
