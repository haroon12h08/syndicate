package com.syndicate.provenance.dto;

import java.util.List;
import java.util.UUID;

/** One input the filing was built from, with the proof that it is part of the root. */
public record ProvenanceLeafDto(
        int index,
        String type,
        UUID referenceId,
        String label,
        String canonical,
        String hash,
        List<ProofStepDto> proof
) {
    public record ProofStepDto(String siblingHash, String position) {
    }
}
