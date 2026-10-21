package com.syndicate.ai.artifact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AiArtifactRepository extends JpaRepository<AiArtifact, UUID> {

    List<AiArtifact> findByReviewDecision(String reviewDecision);

    List<AiArtifact> findByResultingEntityTypeAndResultingEntityId(String resultingEntityType, UUID resultingEntityId);

    @Query(value = "SELECT * FROM ai_artifacts WHERE input_entity_ids @> CAST(:entityIdJson AS jsonb)", nativeQuery = true)
    List<AiArtifact> findByInputEntityId(@Param("entityIdJson") String entityIdJson);
}
