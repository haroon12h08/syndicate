package com.syndicate.evidence;

import com.syndicate.common.BaseEntity;
import com.syndicate.fact.Fact;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "evidence")
public class Evidence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstream_id", nullable = false)
    private Workstream workstream;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private EvidenceDocumentType documentType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedByUser;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "file_sha256")
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus;

    @Column(name = "processing_error", columnDefinition = "TEXT")
    private String processingError;

    @ManyToMany(mappedBy = "evidence", fetch = FetchType.LAZY)
    private Set<Fact> facts = new HashSet<>();

    protected Evidence() {
    }

    public Evidence(Workstream workstream, String fileName, String storagePath, String contentType,
                     long fileSizeBytes, EvidenceDocumentType documentType, User uploadedByUser, String fileSha256) {
        this.workstream = workstream;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.documentType = documentType;
        this.uploadedByUser = uploadedByUser;
        this.uploadedAt = Instant.now();
        this.fileSha256 = fileSha256;
        this.processingStatus = ProcessingStatus.PENDING;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public EvidenceDocumentType getDocumentType() {
        return documentType;
    }

    public User getUploadedByUser() {
        return uploadedByUser;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }
}
