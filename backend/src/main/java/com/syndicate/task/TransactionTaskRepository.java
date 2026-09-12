package com.syndicate.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionTaskRepository extends JpaRepository<TransactionTask, UUID> {
    List<TransactionTask> findByTransactionId(UUID transactionId);

    Optional<TransactionTask> findByTransactionIdAndSourceRuleId(UUID transactionId, UUID sourceRuleId);
}
