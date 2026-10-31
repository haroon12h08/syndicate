package com.syndicate.captable;

import com.syndicate.common.BaseEntity;
import com.syndicate.evidence.Evidence;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "share_encumbrances")
public class ShareEncumbrance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private CapTableHolder holder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_class_id", nullable = false)
    private SecurityClass securityClass;

    @Column(name = "encumbered_shares", nullable = false, precision = 20, scale = 0)
    private BigDecimal encumberedShares;

    @Enumerated(EnumType.STRING)
    @Column(name = "encumbrance_type", nullable = false, length = 32)
    private EncumbranceType encumbranceType;

    @Column(name = "pledgee_name", nullable = false, length = 255)
    private String pledgeeName;

    @Column(name = "pledge_date", nullable = false)
    private LocalDate pledgeDate;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE"; // ACTIVE, RELEASED, INVOKED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id")
    private Evidence evidence;

    protected ShareEncumbrance() {
    }

    public ShareEncumbrance(Transaction transaction, CapTableHolder holder,
                            SecurityClass securityClass, BigDecimal encumberedShares,
                            EncumbranceType encumbranceType, String pledgeeName,
                            LocalDate pledgeDate, LocalDate releaseDate,
                            String status, Evidence evidence) {
        this.transaction = transaction;
        this.holder = holder;
        this.securityClass = securityClass;
        this.encumberedShares = encumberedShares;
        this.encumbranceType = encumbranceType;
        this.pledgeeName = pledgeeName;
        this.pledgeDate = pledgeDate;
        this.releaseDate = releaseDate;
        this.status = status != null ? status : "ACTIVE";
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

    public BigDecimal getEncumberedShares() {
        return encumberedShares;
    }

    public void setEncumberedShares(BigDecimal encumberedShares) {
        this.encumberedShares = encumberedShares;
    }

    public EncumbranceType getEncumbranceType() {
        return encumbranceType;
    }

    public void setEncumbranceType(EncumbranceType encumbranceType) {
        this.encumbranceType = encumbranceType;
    }

    public String getPledgeeName() {
        return pledgeeName;
    }

    public void setPledgeeName(String pledgeeName) {
        this.pledgeeName = pledgeeName;
    }

    public LocalDate getPledgeDate() {
        return pledgeDate;
    }

    public void setPledgeDate(LocalDate pledgeDate) {
        this.pledgeDate = pledgeDate;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Evidence getEvidence() {
        return evidence;
    }

    public void setEvidence(Evidence evidence) {
        this.evidence = evidence;
    }
}
