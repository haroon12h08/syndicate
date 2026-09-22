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
import java.util.UUID;

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

    @Column(name = "lineage_id", nullable = false, updatable = false)
    private UUID lineageId;

    @Column(nullable = false, updatable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_evidence_id", updatable = false)
    private Evidence parentEvidence;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_state", nullable = false)
    private RetentionState retentionState = RetentionState.ACTIVE;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by_user_id")
    private User archivedByUser;

    @Column(name = "archive_reason")
    private String archiveReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceQuality quality = EvidenceQuality.UNREVIEWED;

    @Column(name = "quality_reason")
    private String qualityReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quality_reviewed_by_user_id")
    private User qualityReviewedByUser;

    @Column(name = "quality_reviewed_at")
    private Instant qualityReviewedAt;

    @Column(name = "access_classification", nullable = false)
    private String accessClassification = "CONFIDENTIAL";

    @Column(nullable = false)
    private String source = "UPLOAD";

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
        this.lineageId = UUID.randomUUID();
        this.version = 1;
    }

    /** A corrected or replacement copy of {@code parent}; the parent is left untouched. */
    public static Evidence newVersionOf(Evidence parent, String fileName, String storagePath, String contentType,
                                        long fileSizeBytes, User uploadedByUser, String fileSha256) {
        Evidence next = new Evidence(parent.getWorkstream(), fileName, storagePath, contentType, fileSizeBytes,
                parent.getDocumentType(), uploadedByUser, fileSha256);
        next.lineageId = parent.getLineageId();
        next.version = parent.getVersion() + 1;
        next.parentEvidence = parent;
        return next;
    }

    public void markSuperseded(Instant at) {
        this.supersededAt = at;
        this.quality = EvidenceQuality.SUPERSEDED;
    }

    public void assessQuality(EvidenceQuality quality, String reason, User reviewer, Instant at) {
        this.quality = quality;
        this.qualityReason = reason;
        this.qualityReviewedByUser = reviewer;
        this.qualityReviewedAt = at;
    }

    public EvidenceQuality getQuality() {
        return quality;
    }

    public String getQualityReason() {
        return qualityReason;
    }

    public User getQualityReviewedByUser() {
        return qualityReviewedByUser;
    }

    public Instant getQualityReviewedAt() {
        return qualityReviewedAt;
    }

    public void archive(User by, String reason, Instant at) {
        this.retentionState = RetentionState.ARCHIVED;
        this.archivedByUser = by;
        this.archiveReason = reason;
        this.archivedAt = at;
    }

    public UUID getLineageId() {
        return lineageId;
    }

    public int getVersion() {
        return version;
    }

    public Evidence getParentEvidence() {
        return parentEvidence;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public RetentionState getRetentionState() {
        return retentionState;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getArchiveReason() {
        return archiveReason;
    }

    public String getAccessClassification() {
        return accessClassification;
    }

    public String getSource() {
        return source;
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
