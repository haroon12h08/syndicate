package com.syndicate.evidence.dto;

import java.util.UUID;

public record EvidenceIntegrityDto(UUID evidenceId, String recordedSha256, String actualSha256, boolean intact) {
}
