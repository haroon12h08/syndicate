package com.syndicate.litigation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LitigationEvidenceLinkId implements Serializable {

    @Column(name = "litigation_id")
    private UUID litigationId;

    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "evidence_role", length = 64)
    private String evidenceRole;

    public LitigationEvidenceLinkId() {
    }

    public LitigationEvidenceLinkId(UUID litigationId, UUID evidenceId, String evidenceRole) {
        this.litigationId = litigationId;
        this.evidenceId = evidenceId;
        this.evidenceRole = evidenceRole;
    }

    public UUID getLitigationId() {
        return litigationId;
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
        LitigationEvidenceLinkId that = (LitigationEvidenceLinkId) o;
        return Objects.equals(litigationId, that.litigationId) &&
                Objects.equals(evidenceId, that.evidenceId) &&
                Objects.equals(evidenceRole, that.evidenceRole);
    }

    @Override
    public int hashCode() {
        return Objects.hash(litigationId, evidenceId, evidenceRole);
    }
}
