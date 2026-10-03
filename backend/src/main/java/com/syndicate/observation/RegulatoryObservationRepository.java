package com.syndicate.observation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegulatoryObservationRepository extends JpaRepository<RegulatoryObservation, UUID> {

    List<RegulatoryObservation> findByTransactionIdOrderByReceivedDateDesc(UUID transactionId);

    List<RegulatoryObservation> findByTransactionIdAndStatusIn(UUID transactionId, List<ObservationStatus> statuses);
}
