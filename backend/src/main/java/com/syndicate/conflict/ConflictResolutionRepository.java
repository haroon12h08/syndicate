package com.syndicate.conflict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConflictResolutionRepository extends JpaRepository<ConflictResolution, UUID> {
}
