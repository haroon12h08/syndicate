package com.syndicate.litigation;

import com.syndicate.evidence.Evidence;
import jakarta.persistence.*;

@Entity
@Table(name = "litigation_evidence_link")
public class LitigationEvidenceLink {

    @EmbeddedId
    private LitigationEvidenceLinkId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("litigationId")
    @JoinColumn(name = "litigation_id")
    private LitigationCase litigationCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("evidenceId")
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    protected LitigationEvidenceLink() {
    }

    public LitigationEvidenceLink(LitigationCase litigationCase, Evidence evidence, String evidenceRole) {
        this.id = new LitigationEvidenceLinkId(litigationCase.getId(), evidence.getId(), evidenceRole);
        this.litigationCase = litigationCase;
        this.evidence = evidence;
    }

    public LitigationEvidenceLinkId getId() {
        return id;
    }

    public LitigationCase getLitigationCase() {
        return litigationCase;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public String getEvidenceRole() {
        return id != null ? id.getEvidenceRole() : null;
    }
}
