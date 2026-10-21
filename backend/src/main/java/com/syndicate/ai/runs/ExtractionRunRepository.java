package com.syndicate.ai.runs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, UUID> {

    List<ExtractionRun> findByEvidenceId(UUID evidenceId);

    List<ExtractionRun> findByEvidenceIdOrderByCreatedAtDesc(UUID evidenceId);
}
