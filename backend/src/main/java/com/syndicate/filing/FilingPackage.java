package com.syndicate.filing;

import com.syndicate.common.BaseEntity;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** The assembled, downloadable filing package for one approved document version (spec §35). */
@Entity
@Immutable
@Table(name = "filing_packages")
public class FilingPackage extends BaseEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drhp_document_id", nullable = false, updatable = false)
    private DrhpDocument document;

    @Column(name = "storage_path", nullable = false, updatable = false)
    private String storagePath;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(name = "merkle_root", updatable = false)
    private String merkleRoot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "built_by_user_id", nullable = false, updatable = false)
    private User builtBy;

    @Column(name = "built_at", nullable = false, updatable = false)
    private Instant builtAt;

    protected FilingPackage() {
    }

    public FilingPackage(UUID transactionId, DrhpDocument document, String storagePath, String fileName,
                         long sizeBytes, String sha256, String merkleRoot, User builtBy) {
        this.transactionId = transactionId;
        this.document = document;
        this.storagePath = storagePath;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.merkleRoot = merkleRoot;
        this.builtBy = builtBy;
        this.builtAt = Instant.now();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public DrhpDocument getDocument() {
        return document;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public User getBuiltBy() {
        return builtBy;
    }

    public Instant getBuiltAt() {
        return builtAt;
    }
}
