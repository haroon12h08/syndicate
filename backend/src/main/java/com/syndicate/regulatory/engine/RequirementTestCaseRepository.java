package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequirementTestCaseRepository extends JpaRepository<RequirementTestCase, UUID> {
    List<RequirementTestCase> findByRequirementVersionId(UUID requirementVersionId);
}
