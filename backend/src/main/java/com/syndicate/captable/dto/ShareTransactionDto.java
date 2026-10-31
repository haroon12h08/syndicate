package com.syndicate.captable.dto;

import com.syndicate.captable.ConsiderationType;
import com.syndicate.captable.CorporateActionType;
import com.syndicate.captable.ShareTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ShareTransactionDto(
        UUID id,
        UUID transactionId,
        UUID securityClassId,
        String securityClassName,
        CorporateActionType eventType,
        LocalDate eventDate,
        UUID sourceHolderId,
        String sourceHolderName,
        UUID destHolderId,
        String destHolderName,
        BigDecimal shareCount,
        BigDecimal pricePerShare,
        BigDecimal faceValueAtEvent,
        ConsiderationType considerationType,
        LocalDate boardResolutionDate,
        LocalDate shareholderResolutionDate,
        String rocFilingRef,
        UUID evidenceId,
        String notes
) {
    public static ShareTransactionDto fromEntity(ShareTransaction st) {
        return new ShareTransactionDto(
                st.getId(),
                st.getTransaction().getId(),
                st.getSecurityClass().getId(),
                st.getSecurityClass().getName(),
                st.getEventType(),
                st.getEventDate(),
                st.getSourceHolder() != null ? st.getSourceHolder().getId() : null,
                st.getSourceHolder() != null ? st.getSourceHolder().getLegalName() : null,
                st.getDestHolder() != null ? st.getDestHolder().getId() : null,
                st.getDestHolder() != null ? st.getDestHolder().getLegalName() : null,
                st.getShareCount(),
                st.getPricePerShare(),
                st.getFaceValueAtEvent(),
                st.getConsiderationType(),
                st.getBoardResolutionDate(),
                st.getShareholderResolutionDate(),
                st.getRocFilingRef(),
                st.getEvidence() != null ? st.getEvidence().getId() : null,
                st.getNotes()
        );
    }
}
