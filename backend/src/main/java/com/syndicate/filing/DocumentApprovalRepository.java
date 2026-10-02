package com.syndicate.filing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentApprovalRepository extends JpaRepository<DocumentApproval, UUID> {

    List<DocumentApproval> findByDocumentId(UUID documentId);

    List<DocumentApproval> findByTransactionId(UUID transactionId);

    Optional<DocumentApproval> findByDocumentIdAndApproverRole(UUID documentId,
            com.syndicate.transaction.TransactionRole role);

    @Query(value = "SELECT required_role FROM document_approval_requirements ORDER BY required_role",
            nativeQuery = true)
    List<String> requiredRoles();
}
