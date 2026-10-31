package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegulatorySnapshotRepository extends JpaRepository<RegulatorySnapshot, UUID> {
    Optional<RegulatorySnapshot> findFirstByTransactionIdAndActiveTrueOrderByPinnedAtDesc(UUID transactionId);
    List<RegulatorySnapshot> findByTransactionIdOrderByPinnedAtDesc(UUID transactionId);
}
