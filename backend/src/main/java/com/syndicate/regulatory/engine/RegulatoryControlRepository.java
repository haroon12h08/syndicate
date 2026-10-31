package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegulatoryControlRepository extends JpaRepository<RegulatoryControl, UUID> {
    Optional<RegulatoryControl> findByCode(String code);
    List<RegulatoryControl> findByRequirementVersionIdOrderByOrderIndexAsc(UUID requirementVersionId);
}
