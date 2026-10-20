package com.syndicate.fact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactRepository extends JpaRepository<Fact, UUID> {
    List<Fact> findByWorkstreamId(UUID workstreamId);

    List<Fact> findByWorkstreamIdAndStatusNot(UUID workstreamId, FactStatus status);

    Optional<Fact> findBySupersedesFactId(UUID supersedesFactId);

    List<Fact> findByWorkstreamTransactionIdAndStatus(UUID transactionId, FactStatus status);

    List<Fact> findByWorkstreamTransactionId(UUID transactionId);

    @Query("SELECT f FROM Fact f JOIN f.evidence e WHERE e.id = :evidenceId")
    List<Fact> findByEvidenceId(@Param("evidenceId") UUID evidenceId);
}
