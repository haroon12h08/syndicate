package com.syndicate.workbench;

import java.util.UUID;

/**
 * One reason the transaction cannot be confidently submitted right now (spec §8), with the object
 * it concerns, why, and what would clear it.
 */
public record BlockerDto(String type, BlockerSeverity severity, String objectType, UUID objectId, String title,
                         String reason, String nextAction, String ownerRole) {
}
