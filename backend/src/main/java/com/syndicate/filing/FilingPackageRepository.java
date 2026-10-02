package com.syndicate.filing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilingPackageRepository extends JpaRepository<FilingPackage, UUID> {

    Optional<FilingPackage> findByDocumentId(UUID documentId);

    List<FilingPackage> findByTransactionIdOrderByBuiltAtDesc(UUID transactionId);
}
