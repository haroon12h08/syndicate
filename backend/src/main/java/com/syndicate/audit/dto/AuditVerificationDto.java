package com.syndicate.audit.dto;

import java.util.UUID;

public record AuditVerificationDto(boolean valid, int verifiedEvents, int legacyUnchainedEvents,
                                   UUID firstInvalidEventId, String problem) {
}
