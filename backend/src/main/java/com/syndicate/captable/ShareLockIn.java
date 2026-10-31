package com.syndicate.captable;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "share_lock_ins")
public class ShareLockIn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private CapTableHolder holder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_class_id", nullable = false)
    private SecurityClass securityClass;

    @Column(name = "locked_share_count", nullable = false, precision = 20, scale = 0)
    private BigDecimal lockedShareCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_in_category", nullable = false, length = 64)
    private LockInTier lockInCategory;

    @Column(name = "regulatory_rule_id")
    private UUID regulatoryRuleId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "is_statutory", nullable = false)
    private boolean statutory = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "exemption_claimed", length = 64)
    private LockInExemption exemptionClaimed = LockInExemption.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    protected ShareLockIn() {
    }

    public ShareLockIn(Transaction transaction, CapTableHolder holder,
                       SecurityClass securityClass, BigDecimal lockedShareCount,
                       LockInTier lockInCategory, UUID regulatoryRuleId,
                       LocalDate startDate, LocalDate expiryDate,
                       boolean statutory, LockInExemption exemptionClaimed,
                       Evidence evidence) {
        this.transaction = transaction;
        this.holder = holder;
        this.securityClass = securityClass;
        this.lockedShareCount = lockedShareCount;
        this.lockInCategory = lockInCategory;
        this.regulatoryRuleId = regulatoryRuleId;
        this.startDate = startDate;
        this.expiryDate = expiryDate;
        this.statutory = statutory;
        this.exemptionClaimed = exemptionClaimed != null ? exemptionClaimed : LockInExemption.NONE;
        this.evidence = evidence;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public CapTableHolder getHolder() {
        return holder;
    }

    public void setHolder(CapTableHolder holder) {
        this.holder = holder;
    }

    public SecurityClass getSecurityClass() {
        return securityClass;
    }

    public void setSecurityClass(SecurityClass securityClass) {
        this.securityClass = securityClass;
    }

    public BigDecimal getLockedShareCount() {
        return lockedShareCount;
    }

    public void setLockedShareCount(BigDecimal lockedShareCount) {
        this.lockedShareCount = lockedShareCount;
    }

    public LockInTier getLockInCategory() {
        return lockInCategory;
    }

    public void setLockInCategory(LockInTier lockInCategory) {
        this.lockInCategory = lockInCategory;
    }

    public UUID getRegulatoryRuleId() {
        return regulatoryRuleId;
    }

    public void setRegulatoryRuleId(UUID regulatoryRuleId) {
        this.regulatoryRuleId = regulatoryRuleId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isStatutory() {
        return statutory;
    }

    public void setStatutory(boolean statutory) {
        this.statutory = statutory;
    }

    public LockInExemption getExemptionClaimed() {
        return exemptionClaimed;
    }

    public void setExemptionClaimed(LockInExemption exemptionClaimed) {
        this.exemptionClaimed = exemptionClaimed;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public void setEvidence(Evidence evidence) {
        this.evidence = evidence;
    }
}
