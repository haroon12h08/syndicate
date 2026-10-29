package com.syndicate.ai.suggestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, UUID> {

    List<AiSuggestion> findByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    List<AiSuggestion> findByTransactionIdAndStatusOrderByCreatedAtDesc(UUID transactionId, SuggestionStatus status);

    long countByTransactionIdAndStatus(UUID transactionId, SuggestionStatus status);

    Optional<AiSuggestion> findFirstByTransactionIdAndTargetTypeAndTargetIdAndSuggestionTypeAndStatus(
            UUID transactionId, String targetType, UUID targetId, SuggestionType suggestionType, SuggestionStatus status
    );
}
