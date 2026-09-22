package com.syndicate.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByWorkstreamId(UUID workstreamId);

    List<Evidence> findByWorkstreamTransactionIdOrderByUploadedAtDesc(UUID transactionId);

    Optional<Evidence> findFirstByWorkstreamTransactionIdAndFileSha256AndRetentionStateNot(
            UUID transactionId, String fileSha256, RetentionState retentionState);

    List<Evidence> findByLineageIdOrderByVersionAsc(UUID lineageId);

    @Query(value = "SELECT count(*) FROM fact_evidence_link WHERE evidence_id = :id", nativeQuery = true)
    long countFactReferences(@Param("id") UUID evidenceId);
}
