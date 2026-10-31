package com.syndicate.captable;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "security_classes")
public class SecurityClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_type", nullable = false, length = 32)
    private SecurityType securityType;

    @Column(length = 32)
    private String isin;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Column(name = "face_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal faceValue;

    @Column(name = "voting_rights_per_share", nullable = false, precision = 10, scale = 2)
    private BigDecimal votingRightsPerShare = BigDecimal.ONE;

    @Column(name = "authorized_shares", nullable = false, precision = 20, scale = 0)
    private BigDecimal authorizedShares;

    @Column(name = "is_convertible", nullable = false)
    private boolean convertible = false;

    @Column(name = "conversion_ratio", precision = 15, scale = 6)
    private BigDecimal conversionRatio = BigDecimal.ONE;

    @Column(name = "conversion_price", precision = 15, scale = 2)
    private BigDecimal conversionPrice;

    @Column(name = "conversion_deadline")
    private LocalDate conversionDeadline;

    protected SecurityClass() {
    }

    public SecurityClass(Transaction transaction, String name, SecurityType securityType,
                         String isin, String currency, BigDecimal faceValue,
                         BigDecimal votingRightsPerShare, BigDecimal authorizedShares,
                         boolean convertible, BigDecimal conversionRatio,
                         BigDecimal conversionPrice, LocalDate conversionDeadline) {
        this.transaction = transaction;
        this.name = name;
        this.securityType = securityType;
        this.isin = isin;
        this.currency = currency != null ? currency : "INR";
        this.faceValue = faceValue;
        this.votingRightsPerShare = votingRightsPerShare != null ? votingRightsPerShare : BigDecimal.ONE;
        this.authorizedShares = authorizedShares;
        this.convertible = convertible;
        this.conversionRatio = conversionRatio != null ? conversionRatio : BigDecimal.ONE;
        this.conversionPrice = conversionPrice;
        this.conversionDeadline = conversionDeadline;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SecurityType getSecurityType() {
        return securityType;
    }

    public void setSecurityType(SecurityType securityType) {
        this.securityType = securityType;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getFaceValue() {
        return faceValue;
    }

    public void setFaceValue(BigDecimal faceValue) {
        this.faceValue = faceValue;
    }

    public BigDecimal getVotingRightsPerShare() {
        return votingRightsPerShare;
    }

    public void setVotingRightsPerShare(BigDecimal votingRightsPerShare) {
        this.votingRightsPerShare = votingRightsPerShare;
    }

    public BigDecimal getAuthorizedShares() {
        return authorizedShares;
    }

    public void setAuthorizedShares(BigDecimal authorizedShares) {
        this.authorizedShares = authorizedShares;
    }

    public boolean isConvertible() {
        return convertible;
    }

    public void setConvertible(boolean convertible) {
        this.convertible = convertible;
    }

    public BigDecimal getConversionRatio() {
        return conversionRatio;
    }

    public void setConversionRatio(BigDecimal conversionRatio) {
        this.conversionRatio = conversionRatio;
    }

    public BigDecimal getConversionPrice() {
        return conversionPrice;
    }

    public void setConversionPrice(BigDecimal conversionPrice) {
        this.conversionPrice = conversionPrice;
    }

    public LocalDate getConversionDeadline() {
        return conversionDeadline;
    }

    public void setConversionDeadline(LocalDate conversionDeadline) {
        this.conversionDeadline = conversionDeadline;
    }
}
