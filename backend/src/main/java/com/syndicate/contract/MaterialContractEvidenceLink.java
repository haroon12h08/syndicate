package com.syndicate.contract;

import com.syndicate.evidence.Evidence;
import jakarta.persistence.*;

@Entity
@Table(name = "material_contract_evidence_link")
public class MaterialContractEvidenceLink {

    @EmbeddedId
    private MaterialContractEvidenceLinkId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contractId")
    @JoinColumn(name = "contract_id")
    private MaterialContract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("evidenceId")
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    protected MaterialContractEvidenceLink() {
    }

    public MaterialContractEvidenceLink(MaterialContract contract, Evidence evidence, String evidenceRole) {
        this.id = new MaterialContractEvidenceLinkId(contract.getId(), evidence.getId(), evidenceRole);
        this.contract = contract;
        this.evidence = evidence;
    }

    public MaterialContractEvidenceLinkId getId() {
        return id;
    }

    public MaterialContract getContract() {
        return contract;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public String getEvidenceRole() {
        return id != null ? id.getEvidenceRole() : null;
    }
}
