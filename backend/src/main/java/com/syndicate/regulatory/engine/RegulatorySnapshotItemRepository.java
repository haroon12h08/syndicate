package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegulatorySnapshotItemRepository extends JpaRepository<RegulatorySnapshotItem, UUID> {
    List<RegulatorySnapshotItem> findBySnapshotId(UUID snapshotId);
    void deleteBySnapshotId(UUID snapshotId);
}
