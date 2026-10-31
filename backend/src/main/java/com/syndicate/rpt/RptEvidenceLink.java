package com.syndicate.rpt;

import com.syndicate.evidence.Evidence;
import jakarta.persistence.*;

@Entity
@Table(name = "rpt_evidence_link")
public class RptEvidenceLink {

    @EmbeddedId
    private RptEvidenceLinkId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("rptId")
    @JoinColumn(name = "rpt_id")
    private RelatedPartyTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("evidenceId")
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    protected RptEvidenceLink() {
    }

    public RptEvidenceLink(RelatedPartyTransaction transaction, Evidence evidence, String evidenceRole) {
        this.id = new RptEvidenceLinkId(transaction.getId(), evidence.getId(), evidenceRole);
        this.transaction = transaction;
        this.evidence = evidence;
    }

    public RptEvidenceLinkId getId() {
        return id;
    }

    public RelatedPartyTransaction getTransaction() {
        return transaction;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public String getEvidenceRole() {
        return id != null ? id.getEvidenceRole() : null;
    }
}
