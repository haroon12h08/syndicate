package com.syndicate.regulatory.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ControlEvaluationRepository extends JpaRepository<ControlEvaluation, UUID> {
    List<ControlEvaluation> findByTransactionId(UUID transactionId);
    Optional<ControlEvaluation> findByTransactionIdAndControlId(UUID transactionId, UUID controlId);
    void deleteByTransactionId(UUID transactionId);
}
