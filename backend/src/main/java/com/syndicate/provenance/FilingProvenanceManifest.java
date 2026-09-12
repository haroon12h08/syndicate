package com.syndicate.provenance;

import com.syndicate.common.BaseEntity;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The cryptographic proof that a filing was compiled from an exact state. Written once and never
 * mutated: if the underlying state changes, a new compile produces a new manifest rather than
 * altering this one.
 */
@Entity
@Table(name = "filing_provenance_manifests")
public class FilingProvenanceManifest extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drhp_document_id", nullable = false, unique = true)
    private DrhpDocument drhpDocument;

    @Column(name = "merkle_root", nullable = false)
    private String merkleRoot;

    @Column(name = "leaf_count", nullable = false)
    private int leafCount;

    @Column(nullable = false)
    private String leaves;

    @Column(nullable = false)
    private String algorithm;

    @Column(name = "compiled_at", nullable = false)
    private Instant compiledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compiled_by_user_id")
    private User compiledByUser;

    protected FilingProvenanceManifest() {
    }

    public FilingProvenanceManifest(DrhpDocument drhpDocument, String merkleRoot, int leafCount,
                                     String leaves, User compiledByUser) {
        this.drhpDocument = drhpDocument;
        this.merkleRoot = merkleRoot;
        this.leafCount = leafCount;
        this.leaves = leaves;
        this.algorithm = "SHA-256";
        this.compiledAt = Instant.now();
        this.compiledByUser = compiledByUser;
    }

    public DrhpDocument getDrhpDocument() {
        return drhpDocument;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public int getLeafCount() {
        return leafCount;
    }

    public String getLeaves() {
        return leaves;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Instant getCompiledAt() {
        return compiledAt;
    }

    public User getCompiledByUser() {
        return compiledByUser;
    }
}
