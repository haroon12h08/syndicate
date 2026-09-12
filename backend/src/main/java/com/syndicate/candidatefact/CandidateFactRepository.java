package com.syndicate.candidatefact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateFactRepository extends JpaRepository<CandidateFact, UUID> {
    List<CandidateFact> findByWorkstreamId(UUID workstreamId);

    List<CandidateFact> findByWorkstreamIdAndStatus(UUID workstreamId, CandidateFactStatus status);

    void deleteByEvidenceId(UUID evidenceId);

    /** The accepted extraction a fact came from, when it originated from a document. */
    Optional<CandidateFact> findByResultingFactId(UUID factId);
}
