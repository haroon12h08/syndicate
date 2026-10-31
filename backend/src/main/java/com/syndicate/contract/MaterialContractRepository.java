package com.syndicate.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialContractRepository extends JpaRepository<MaterialContract, UUID> {
    List<MaterialContract> findByTransactionId(UUID transactionId);
    List<MaterialContract> findByTransactionIdAndContractCategory(UUID transactionId, String contractCategory);
    List<MaterialContract> findByTransactionIdAndAbove10PctScreeningIndicator(UUID transactionId, boolean above10Pct);
    List<MaterialContract> findByTransactionIdAndHasChangeOfControl(UUID transactionId, boolean hasChangeOfControl);
}
