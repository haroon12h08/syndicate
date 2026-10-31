package com.syndicate.litigation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LitigationCaseRepository extends JpaRepository<LitigationCase, UUID> {
    List<LitigationCase> findByTransactionId(UUID transactionId);
    List<LitigationCase> findByTransactionIdAndCategory(UUID transactionId, String category);
    List<LitigationCase> findByTransactionIdAndMaterial(UUID transactionId, boolean material);
    List<LitigationCase> findByWorkstreamId(UUID workstreamId);
}
