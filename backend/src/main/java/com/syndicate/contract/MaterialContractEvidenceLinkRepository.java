package com.syndicate.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialContractEvidenceLinkRepository extends JpaRepository<MaterialContractEvidenceLink, MaterialContractEvidenceLinkId> {
    List<MaterialContractEvidenceLink> findByIdContractId(UUID contractId);
    void deleteByIdContractId(UUID contractId);
}
