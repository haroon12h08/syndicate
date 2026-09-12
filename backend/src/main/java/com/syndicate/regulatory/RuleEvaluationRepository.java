package com.syndicate.regulatory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleEvaluationRepository extends JpaRepository<RuleEvaluation, UUID> {
    List<RuleEvaluation> findByTransactionId(UUID transactionId);

    Optional<RuleEvaluation> findByTransactionIdAndRuleId(UUID transactionId, UUID ruleId);
}
