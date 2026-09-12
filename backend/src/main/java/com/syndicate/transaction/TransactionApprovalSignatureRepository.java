package com.syndicate.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionApprovalSignatureRepository extends JpaRepository<TransactionApprovalSignature, UUID> {
    List<TransactionApprovalSignature> findByTransactionIdAndTransition(UUID transactionId, String transition);

    boolean existsByTransactionIdAndTransitionAndRequiredRole(UUID transactionId, String transition, TransactionRole requiredRole);
}
