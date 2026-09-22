package com.syndicate.workbench;

/**
 * Evidence coverage over current material facts (spec §7.4). Counts, not scores: every number is
 * a count of concrete facts that the blockers list itemises.
 */
public record CoverageDto(int materialFacts, int verified, int withEvidence, int withAcceptableEvidence,
                          int inOpenConflict, int staleDisclosures, int totalDisclosures) {
}
