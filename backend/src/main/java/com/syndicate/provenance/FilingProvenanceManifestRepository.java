package com.syndicate.provenance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FilingProvenanceManifestRepository extends JpaRepository<FilingProvenanceManifest, UUID> {
    Optional<FilingProvenanceManifest> findByDrhpDocumentId(UUID drhpDocumentId);
}
