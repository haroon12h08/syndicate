package com.syndicate.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionMembershipRepository extends JpaRepository<TransactionMembership, UUID> {
    List<TransactionMembership> findByTransactionId(UUID transactionId);

    List<TransactionMembership> findByUserId(UUID userId);

    Optional<TransactionMembership> findByTransactionIdAndUserId(UUID transactionId, UUID userId);

    boolean existsByTransactionIdAndUserId(UUID transactionId, UUID userId);
}
