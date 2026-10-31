package com.syndicate.captable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CapTableHolderRepository extends JpaRepository<CapTableHolder, UUID> {
    List<CapTableHolder> findByTransactionId(UUID transactionId);
    List<CapTableHolder> findByTransactionIdAndCategory(UUID transactionId, HolderCategory category);
}
