package com.syndicate.workstream;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkstreamRepository extends JpaRepository<Workstream, UUID> {
    List<Workstream> findByTransactionId(UUID transactionId);
}
