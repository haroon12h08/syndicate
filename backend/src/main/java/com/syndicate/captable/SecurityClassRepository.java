package com.syndicate.captable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityClassRepository extends JpaRepository<SecurityClass, UUID> {
    List<SecurityClass> findByTransactionId(UUID transactionId);
    List<SecurityClass> findByTransactionIdAndSecurityType(UUID transactionId, SecurityType securityType);
}
