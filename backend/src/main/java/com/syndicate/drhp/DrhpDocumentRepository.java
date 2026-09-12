package com.syndicate.drhp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DrhpDocumentRepository extends JpaRepository<DrhpDocument, UUID> {
    Optional<DrhpDocument> findFirstByTransactionIdOrderByVersionDesc(UUID transactionId);

    List<DrhpDocument> findByTransactionIdOrderByVersionDesc(UUID transactionId);

    /** Compiled documents that printed this fact's value. */
    @Query("select d from DrhpDocument d join d.citedFacts f "
            + "where f.id = :factId and d.status = com.syndicate.drhp.DrhpStatus.COMPILED")
    List<DrhpDocument> findCompiledCitingFact(UUID factId);
}
