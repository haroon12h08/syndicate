package com.syndicate.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MaterialContractEvidenceLinkId implements Serializable {

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "evidence_role", length = 64)
    private String evidenceRole;

    public MaterialContractEvidenceLinkId() {
    }

    public MaterialContractEvidenceLinkId(UUID contractId, UUID evidenceId, String evidenceRole) {
        this.contractId = contractId;
        this.evidenceId = evidenceId;
        this.evidenceRole = evidenceRole;
    }

    public UUID getContractId() {
        return contractId;
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
        MaterialContractEvidenceLinkId that = (MaterialContractEvidenceLinkId) o;
        return Objects.equals(contractId, that.contractId) &&
                Objects.equals(evidenceId, that.evidenceId) &&
                Objects.equals(evidenceRole, that.evidenceRole);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractId, evidenceId, evidenceRole);
    }
}
