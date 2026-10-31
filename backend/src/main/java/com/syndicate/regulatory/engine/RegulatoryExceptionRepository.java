package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegulatoryExceptionRepository extends JpaRepository<RegulatoryException, UUID> {
    List<RegulatoryException> findByTransactionIdOrderByCreatedAtDesc(UUID transactionId);
    Optional<RegulatoryException> findFirstByTransactionIdAndControlIdAndStatus(UUID transactionId, UUID controlId, String status);
    List<RegulatoryException> findByTransactionIdAndStatus(UUID transactionId, String status);
}
