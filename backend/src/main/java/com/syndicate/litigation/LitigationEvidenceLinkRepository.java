package com.syndicate.litigation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LitigationEvidenceLinkRepository extends JpaRepository<LitigationEvidenceLink, LitigationEvidenceLinkId> {
    List<LitigationEvidenceLink> findByIdLitigationId(UUID litigationId);
    void deleteByIdLitigationId(UUID litigationId);
}
