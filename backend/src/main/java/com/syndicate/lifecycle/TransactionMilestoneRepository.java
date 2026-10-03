package com.syndicate.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionMilestoneRepository extends JpaRepository<TransactionMilestone, UUID> {

    List<TransactionMilestone> findByTransactionIdOrderByOccurredOnAsc(UUID transactionId);

    Optional<TransactionMilestone> findByTransactionIdAndType(UUID transactionId, MilestoneType type);
}
