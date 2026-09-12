package com.syndicate.registry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrySnapshotRepository extends JpaRepository<RegistrySnapshot, UUID> {
    List<RegistrySnapshot> findByCompanyIdOrderByFetchedAtDesc(UUID companyId);
}
