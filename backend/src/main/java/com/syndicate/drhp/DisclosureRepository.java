package com.syndicate.drhp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DisclosureRepository extends JpaRepository<Disclosure, UUID> {
    List<Disclosure> findByTransactionIdOrderByOrderIndexAscCreatedAtAsc(UUID transactionId);

    /** Dependency-DAG lookup: which disclosures were built from this fact. */
    @Query("select d from Disclosure d join d.sourceFacts f where f.id = :factId")
    List<Disclosure> findBySourceFactId(UUID factId);
}
