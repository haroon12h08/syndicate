package com.syndicate.captable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShareTransactionRepository extends JpaRepository<ShareTransaction, UUID> {
    List<ShareTransaction> findByTransactionIdOrderByEventDateAscCreatedAtAsc(UUID transactionId);

    @Query("SELECT st FROM ShareTransaction st WHERE st.transaction.id = :txId AND st.eventDate <= :asOfDate ORDER BY st.eventDate ASC, st.createdAt ASC")
    List<ShareTransaction> findByTransactionIdAsOfDate(@Param("txId") UUID transactionId, @Param("asOfDate") LocalDate asOfDate);

    List<ShareTransaction> findByTransactionIdAndSecurityClassId(UUID transactionId, UUID securityClassId);
}
