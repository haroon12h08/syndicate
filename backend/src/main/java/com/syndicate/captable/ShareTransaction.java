package com.syndicate.captable;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "share_transactions")
public class ShareTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_class_id", nullable = false)
    private SecurityClass securityClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private CorporateActionType eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_holder_id")
    private CapTableHolder sourceHolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_holder_id")
    private CapTableHolder destHolder;

    @Column(name = "share_count", nullable = false, precision = 20, scale = 0)
    private BigDecimal shareCount;

    @Column(name = "price_per_share", nullable = false, precision = 15, scale = 2)
    private BigDecimal pricePerShare = BigDecimal.ZERO;

    @Column(name = "face_value_at_event", nullable = false, precision = 15, scale = 2)
    private BigDecimal faceValueAtEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "consideration_type", nullable = false, length = 32)
    private ConsiderationType considerationType;

    @Column(name = "board_resolution_date")
    private LocalDate boardResolutionDate;

    @Column(name = "shareholder_resolution_date")
    private LocalDate shareholderResolutionDate;

    @Column(name = "roc_filing_ref", length = 64)
    private String rocFilingRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    @Column(columnDefinition = "TEXT")
    private String notes;

    protected ShareTransaction() {
    }

    public ShareTransaction(Transaction transaction, SecurityClass securityClass,
                            CorporateActionType eventType, LocalDate eventDate,
                            CapTableHolder sourceHolder, CapTableHolder destHolder,
                            BigDecimal shareCount, BigDecimal pricePerShare,
                            BigDecimal faceValueAtEvent, ConsiderationType considerationType,
                            LocalDate boardResolutionDate, LocalDate shareholderResolutionDate,
                            String rocFilingRef, Evidence evidence, String notes) {
        this.transaction = transaction;
        this.securityClass = securityClass;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.sourceHolder = sourceHolder;
        this.destHolder = destHolder;
        this.shareCount = shareCount;
        this.pricePerShare = pricePerShare != null ? pricePerShare : BigDecimal.ZERO;
        this.faceValueAtEvent = faceValueAtEvent;
        this.considerationType = considerationType;
        this.boardResolutionDate = boardResolutionDate;
        this.shareholderResolutionDate = shareholderResolutionDate;
        this.rocFilingRef = rocFilingRef;
        this.evidence = evidence;
        this.notes = notes;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public SecurityClass getSecurityClass() {
        return securityClass;
    }

    public void setSecurityClass(SecurityClass securityClass) {
        this.securityClass = securityClass;
    }

    public CorporateActionType getEventType() {
        return eventType;
    }

    public void setEventType(CorporateActionType eventType) {
        this.eventType = eventType;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public CapTableHolder getSourceHolder() {
        return sourceHolder;
    }

    public void setSourceHolder(CapTableHolder sourceHolder) {
        this.sourceHolder = sourceHolder;
    }

    public CapTableHolder getDestHolder() {
        return destHolder;
    }

    public void setDestHolder(CapTableHolder destHolder) {
        this.destHolder = destHolder;
    }

    public BigDecimal getShareCount() {
        return shareCount;
    }

    public void setShareCount(BigDecimal shareCount) {
        this.shareCount = shareCount;
    }

    public BigDecimal getPricePerShare() {
        return pricePerShare;
    }

    public void setPricePerShare(BigDecimal pricePerShare) {
        this.pricePerShare = pricePerShare;
    }

    public BigDecimal getFaceValueAtEvent() {
        return faceValueAtEvent;
    }

    public void setFaceValueAtEvent(BigDecimal faceValueAtEvent) {
        this.faceValueAtEvent = faceValueAtEvent;
    }

    public ConsiderationType getConsiderationType() {
        return considerationType;
    }

    public void setConsiderationType(ConsiderationType considerationType) {
        this.considerationType = considerationType;
    }

    public LocalDate getBoardResolutionDate() {
        return boardResolutionDate;
    }

    public void setBoardResolutionDate(LocalDate boardResolutionDate) {
        this.boardResolutionDate = boardResolutionDate;
    }

    public LocalDate getShareholderResolutionDate() {
        return shareholderResolutionDate;
    }

    public void setShareholderResolutionDate(LocalDate shareholderResolutionDate) {
        this.shareholderResolutionDate = shareholderResolutionDate;
    }

    public String getRocFilingRef() {
        return rocFilingRef;
    }

    public void setRocFilingRef(String rocFilingRef) {
        this.rocFilingRef = rocFilingRef;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public void setEvidence(Evidence evidence) {
        this.evidence = evidence;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
