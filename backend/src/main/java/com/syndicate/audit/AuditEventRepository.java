package com.syndicate.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByTransactionIdOrderByOccurredAtDesc(UUID transactionId);

    Optional<AuditEvent> findTopByTransactionIdAndHashIsNotNullOrderByChainSeqDesc(UUID transactionId);

    Optional<AuditEvent> findTopByTransactionIdIsNullAndHashIsNotNullOrderByChainSeqDesc();

    List<AuditEvent> findByTransactionIdOrderByChainSeqAsc(UUID transactionId);

    /** Serializes chain appends per transaction for the rest of the database transaction. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:chainKey))) l", nativeQuery = true)
    Integer lockChain(@Param("chainKey") String chainKey);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, UUID entityId);
}
