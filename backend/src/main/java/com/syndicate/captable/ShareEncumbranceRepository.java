package com.syndicate.captable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShareEncumbranceRepository extends JpaRepository<ShareEncumbrance, UUID> {
    List<ShareEncumbrance> findByTransactionId(UUID transactionId);
    List<ShareEncumbrance> findByTransactionIdAndStatus(UUID transactionId, String status);
    List<ShareEncumbrance> findByTransactionIdAndHolderId(UUID transactionId, UUID holderId);
}
