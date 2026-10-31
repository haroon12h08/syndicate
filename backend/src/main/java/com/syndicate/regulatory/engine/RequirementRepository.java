package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, UUID> {
    Optional<Requirement> findByCode(String code);
}
