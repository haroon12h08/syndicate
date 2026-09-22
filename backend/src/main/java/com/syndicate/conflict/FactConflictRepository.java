package com.syndicate.conflict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactConflictRepository extends JpaRepository<FactConflict, UUID> {

    Optional<FactConflict> findByConflictKey(String conflictKey);

    List<FactConflict> findByTransactionIdOrderByDetectedAtDesc(UUID transactionId);

    List<FactConflict> findByTransactionIdAndStatus(UUID transactionId, ConflictStatus status);

    @Query("select c from FactConflict c join c.members m where m.id = :factId and c.status = :status")
    List<FactConflict> findByMemberAndStatus(@Param("factId") UUID factId, @Param("status") ConflictStatus status);
}
