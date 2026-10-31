package com.syndicate.rpt;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "related_party_transactions")
public class RelatedPartyTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "related_party_id", nullable = false)
    private RelatedParty relatedParty;

    @Column(name = "financial_year", nullable = false, length = 32)
    private String financialYear;

    @Column(name = "transaction_type", nullable = false, length = 64)
    private String transactionType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Column(name = "in_ordinary_course", nullable = false)
    private boolean inOrdinaryCourse = true;

    @Column(name = "is_arms_length", nullable = false)
    private boolean armsLength = true;

    @Column(name = "arm_length_basis", length = 512)
    private String armLengthBasis;

    @Column(name = "audit_committee_approval", nullable = false, length = 32)
    private String auditCommitteeApproval = "NONE"; // OMNIBUS, SPECIFIC, EXEMPT, NONE

    @Column(name = "audit_committee_approval_date")
    private LocalDate auditCommitteeApprovalDate;

    @Column(name = "audit_committee_max_amount", precision = 18, scale = 2)
    private BigDecimal auditCommitteeMaxAmount;

    @Column(name = "board_approval_date")
    private LocalDate boardApprovalDate;

    @Column(name = "shareholder_approval_required", nullable = false)
    private boolean shareholderApprovalRequired = false;

    @Column(name = "shareholder_approval_date")
    private LocalDate shareholderApprovalDate;

    @Column(name = "ca2013_sec188_applicable", nullable = false)
    private boolean ca2013Sec188Applicable = false;

    @Column(name = "sebi_lodr_material", nullable = false)
    private boolean sebiLodrMaterial = false;

    protected RelatedPartyTransaction() {
    }

    public RelatedPartyTransaction(Transaction transaction, RelatedParty relatedParty,
                                   String financialYear, String transactionType,
                                   BigDecimal amount, String currency,
                                   boolean inOrdinaryCourse, boolean armsLength,
                                   String armLengthBasis, String auditCommitteeApproval,
                                   LocalDate auditCommitteeApprovalDate, BigDecimal auditCommitteeMaxAmount,
                                   LocalDate boardApprovalDate, boolean shareholderApprovalRequired,
                                   LocalDate shareholderApprovalDate, boolean ca2013Sec188Applicable,
                                   boolean sebiLodrMaterial) {
        this.transaction = transaction;
        this.relatedParty = relatedParty;
        this.financialYear = financialYear;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency != null ? currency : "INR";
        this.inOrdinaryCourse = inOrdinaryCourse;
        this.armsLength = armsLength;
        this.armLengthBasis = armLengthBasis;
        this.auditCommitteeApproval = auditCommitteeApproval != null ? auditCommitteeApproval : "NONE";
        this.auditCommitteeApprovalDate = auditCommitteeApprovalDate;
        this.auditCommitteeMaxAmount = auditCommitteeMaxAmount;
        this.boardApprovalDate = boardApprovalDate;
        this.shareholderApprovalRequired = shareholderApprovalRequired;
        this.shareholderApprovalDate = shareholderApprovalDate;
        this.ca2013Sec188Applicable = ca2013Sec188Applicable;
        this.sebiLodrMaterial = sebiLodrMaterial;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public RelatedParty getRelatedParty() {
        return relatedParty;
    }

    public void setRelatedParty(RelatedParty relatedParty) {
        this.relatedParty = relatedParty;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isInOrdinaryCourse() {
        return inOrdinaryCourse;
    }

    public void setInOrdinaryCourse(boolean inOrdinaryCourse) {
        this.inOrdinaryCourse = inOrdinaryCourse;
    }

    public boolean isArmsLength() {
        return armsLength;
    }

    public void setArmsLength(boolean armsLength) {
        this.armsLength = armsLength;
    }

    public String getArmLengthBasis() {
        return armLengthBasis;
    }

    public void setArmLengthBasis(String armLengthBasis) {
        this.armLengthBasis = armLengthBasis;
    }

    public String getAuditCommitteeApproval() {
        return auditCommitteeApproval;
    }

    public void setAuditCommitteeApproval(String auditCommitteeApproval) {
        this.auditCommitteeApproval = auditCommitteeApproval;
    }

    public LocalDate getAuditCommitteeApprovalDate() {
        return auditCommitteeApprovalDate;
    }

    public void setAuditCommitteeApprovalDate(LocalDate auditCommitteeApprovalDate) {
        this.auditCommitteeApprovalDate = auditCommitteeApprovalDate;
    }

    public BigDecimal getAuditCommitteeMaxAmount() {
        return auditCommitteeMaxAmount;
    }

    public void setAuditCommitteeMaxAmount(BigDecimal auditCommitteeMaxAmount) {
        this.auditCommitteeMaxAmount = auditCommitteeMaxAmount;
    }

    public LocalDate getBoardApprovalDate() {
        return boardApprovalDate;
    }

    public void setBoardApprovalDate(LocalDate boardApprovalDate) {
        this.boardApprovalDate = boardApprovalDate;
    }

    public boolean isShareholderApprovalRequired() {
        return shareholderApprovalRequired;
    }

    public void setShareholderApprovalRequired(boolean shareholderApprovalRequired) {
        this.shareholderApprovalRequired = shareholderApprovalRequired;
    }

    public LocalDate getShareholderApprovalDate() {
        return shareholderApprovalDate;
    }

    public void setShareholderApprovalDate(LocalDate shareholderApprovalDate) {
        this.shareholderApprovalDate = shareholderApprovalDate;
    }

    public boolean isCa2013Sec188Applicable() {
        return ca2013Sec188Applicable;
    }

    public void setCa2013Sec188Applicable(boolean ca2013Sec188Applicable) {
        this.ca2013Sec188Applicable = ca2013Sec188Applicable;
    }

    public boolean isSebiLodrMaterial() {
        return sebiLodrMaterial;
    }

    public void setSebiLodrMaterial(boolean sebiLodrMaterial) {
        this.sebiLodrMaterial = sebiLodrMaterial;
    }
}
