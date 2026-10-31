package com.syndicate.captable;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ipo_issue_parameters")
public class IpoIssueParameters {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "fresh_issue_shares", nullable = false, precision = 20, scale = 0)
    private BigDecimal freshIssueShares = BigDecimal.ZERO;

    @Column(name = "ofs_shares", nullable = false, precision = 20, scale = 0)
    private BigDecimal ofsShares = BigDecimal.ZERO;

    @Column(name = "price_band_lower", precision = 15, scale = 2)
    private BigDecimal priceBandLower;

    @Column(name = "price_band_upper", precision = 15, scale = 2)
    private BigDecimal priceBandUpper;

    @Column(name = "expected_allotment_date")
    private LocalDate expectedAllotmentDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected IpoIssueParameters() {
    }

    public IpoIssueParameters(UUID transactionId, BigDecimal freshIssueShares,
                              BigDecimal ofsShares, BigDecimal priceBandLower,
                              BigDecimal priceBandUpper, LocalDate expectedAllotmentDate) {
        this.transactionId = transactionId;
        this.freshIssueShares = freshIssueShares != null ? freshIssueShares : BigDecimal.ZERO;
        this.ofsShares = ofsShares != null ? ofsShares : BigDecimal.ZERO;
        this.priceBandLower = priceBandLower;
        this.priceBandUpper = priceBandUpper;
        this.expectedAllotmentDate = expectedAllotmentDate;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public BigDecimal getFreshIssueShares() {
        return freshIssueShares;
    }

    public void setFreshIssueShares(BigDecimal freshIssueShares) {
        this.freshIssueShares = freshIssueShares;
    }

    public BigDecimal getOfsShares() {
        return ofsShares;
    }

    public void setOfsShares(BigDecimal ofsShares) {
        this.ofsShares = ofsShares;
    }

    public BigDecimal getPriceBandLower() {
        return priceBandLower;
    }

    public void setPriceBandLower(BigDecimal priceBandLower) {
        this.priceBandLower = priceBandLower;
    }

    public BigDecimal getPriceBandUpper() {
        return priceBandUpper;
    }

    public void setPriceBandUpper(BigDecimal priceBandUpper) {
        this.priceBandUpper = priceBandUpper;
    }

    public LocalDate getExpectedAllotmentDate() {
        return expectedAllotmentDate;
    }

    public void setExpectedAllotmentDate(LocalDate expectedAllotmentDate) {
        this.expectedAllotmentDate = expectedAllotmentDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public BigDecimal getTotalIssueShares() {
        return (freshIssueShares != null ? freshIssueShares : BigDecimal.ZERO)
                .add(ofsShares != null ? ofsShares : BigDecimal.ZERO);
    }
}
