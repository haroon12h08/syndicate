package com.syndicate.rpt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RelatedPartyRepository extends JpaRepository<RelatedParty, UUID> {
    List<RelatedParty> findByTransactionId(UUID transactionId);
    List<RelatedParty> findByTransactionIdAndSuggested(UUID transactionId, boolean suggested);
}
