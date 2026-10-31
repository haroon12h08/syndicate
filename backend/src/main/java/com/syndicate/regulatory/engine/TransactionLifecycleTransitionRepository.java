package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionLifecycleTransitionRepository extends JpaRepository<TransactionLifecycleTransition, UUID> {
    @Query("SELECT t FROM TransactionLifecycleTransition t JOIN FETCH t.actor JOIN FETCH t.transaction WHERE t.transaction.id = :transactionId ORDER BY t.transitionedAt DESC")
    List<TransactionLifecycleTransition> findByTransactionIdOrderByTransitionedAtDesc(@Param("transactionId") UUID transactionId);
}
