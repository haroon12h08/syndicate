package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequirementVersionRepository extends JpaRepository<RequirementVersion, UUID> {
    List<RequirementVersion> findByRequirementIdOrderByVersionNumberDesc(UUID requirementId);
    Optional<RequirementVersion> findByRequirementIdAndVersionNumber(UUID requirementId, int versionNumber);
    List<RequirementVersion> findByStatus(String status);
}
