package com.syndicate.captable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShareLockInRepository extends JpaRepository<ShareLockIn, UUID> {
    List<ShareLockIn> findByTransactionId(UUID transactionId);
    List<ShareLockIn> findByTransactionIdAndHolderId(UUID transactionId, UUID holderId);
    void deleteByTransactionId(UUID transactionId);
}
