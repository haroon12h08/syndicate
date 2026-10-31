package com.syndicate.captable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IpoIssueParametersRepository extends JpaRepository<IpoIssueParameters, UUID> {
    Optional<IpoIssueParameters> findByTransactionId(UUID transactionId);
}
