package com.syndicate.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByWorkstreamId(UUID workstreamId);

    List<Evidence> findByWorkstreamTransactionIdOrderByUploadedAtDesc(UUID transactionId);
}
