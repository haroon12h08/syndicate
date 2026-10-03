package com.syndicate.diligence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DiligenceQuestionRepository extends JpaRepository<DiligenceQuestion, UUID> {

    List<DiligenceQuestion> findByTransactionIdOrderByWorkstreamTypeAscCreatedAtAsc(UUID transactionId);

    long countByTransactionIdAndStatus(UUID transactionId, DiligenceStatus status);

    /** The standard question set for a transaction type, in the order it should be worked. */
    @Query(value = "SELECT code, question, workstream_type, severity FROM diligence_question_library "
            + "WHERE active AND transaction_type = :type ORDER BY workstream_type, code", nativeQuery = true)
    List<Object[]> library(String type);
}
