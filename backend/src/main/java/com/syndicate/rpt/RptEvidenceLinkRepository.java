package com.syndicate.rpt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RptEvidenceLinkRepository extends JpaRepository<RptEvidenceLink, RptEvidenceLinkId> {
    List<RptEvidenceLink> findByIdRptId(UUID rptId);
    void deleteByIdRptId(UUID rptId);
}
