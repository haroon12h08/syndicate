package com.syndicate.rpt;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RptEvidenceLinkId implements Serializable {

    @Column(name = "rpt_id")
    private UUID rptId;

    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "evidence_role", length = 64)
    private String evidenceRole;

    public RptEvidenceLinkId() {
    }

    public RptEvidenceLinkId(UUID rptId, UUID evidenceId, String evidenceRole) {
        this.rptId = rptId;
        this.evidenceId = evidenceId;
        this.evidenceRole = evidenceRole;
    }

    public UUID getRptId() {
        return rptId;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public String getEvidenceRole() {
        return evidenceRole;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RptEvidenceLinkId that = (RptEvidenceLinkId) o;
        return Objects.equals(rptId, that.rptId) &&
                Objects.equals(evidenceId, that.evidenceId) &&
                Objects.equals(evidenceRole, that.evidenceRole);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rptId, evidenceId, evidenceRole);
    }
}
