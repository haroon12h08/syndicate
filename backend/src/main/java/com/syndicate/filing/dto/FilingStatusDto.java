package com.syndicate.filing.dto;

import com.syndicate.transaction.TransactionRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the team needs to know about getting this document out of the door: who has approved
 * it, who still must, what is in the way, and the package once it exists.
 */
public record FilingStatusDto(UUID documentId, Integer documentVersion, String documentStatus,
                              List<ApprovalDto> approvals, List<TransactionRole> awaitingApprovalFrom,
                              List<String> blockers, boolean readyToAssemble, PackageDto filingPackage) {

    public record PackageDto(UUID id, String fileName, long sizeBytes, String sha256, String merkleRoot,
                             Instant builtAt, String builtByName) {
    }
}
