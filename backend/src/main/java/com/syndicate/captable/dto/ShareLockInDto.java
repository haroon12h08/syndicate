package com.syndicate.captable.dto;

import com.syndicate.captable.LockInExemption;
import com.syndicate.captable.LockInTier;
import com.syndicate.captable.ShareLockIn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ShareLockInDto(
        UUID id,
        UUID transactionId,
        UUID holderId,
        String holderName,
        UUID securityClassId,
        String securityClassName,
        BigDecimal lockedShareCount,
        LockInTier lockInCategory,
        UUID regulatoryRuleId,
        LocalDate startDate,
        LocalDate expiryDate,
        boolean statutory,
        LockInExemption exemptionClaimed,
        UUID evidenceId
) {
    public static ShareLockInDto fromEntity(ShareLockIn sl) {
        return new ShareLockInDto(
                sl.getId(),
                sl.getTransaction().getId(),
                sl.getHolder().getId(),
                sl.getHolder().getLegalName(),
                sl.getSecurityClass().getId(),
                sl.getSecurityClass().getName(),
                sl.getLockedShareCount(),
                sl.getLockInCategory(),
                sl.getRegulatoryRuleId(),
                sl.getStartDate(),
                sl.getExpiryDate(),
                sl.isStatutory(),
                sl.getExemptionClaimed(),
                sl.getEvidence() != null ? sl.getEvidence().getId() : null
        );
    }
}
