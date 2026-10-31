package com.syndicate.rpt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RelatedPartyTransactionRepository extends JpaRepository<RelatedPartyTransaction, UUID> {
    List<RelatedPartyTransaction> findByTransactionId(UUID transactionId);
    List<RelatedPartyTransaction> findByTransactionIdAndRelatedPartyId(UUID transactionId, UUID relatedPartyId);
    List<RelatedPartyTransaction> findByTransactionIdAndFinancialYear(UUID transactionId, String financialYear);
}
