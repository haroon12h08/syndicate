package com.syndicate.contract;

import com.syndicate.common.BaseEntity;
import com.syndicate.rpt.RelatedParty;
import com.syndicate.transaction.Transaction;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "material_contracts")
public class MaterialContract extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstream_id", nullable = false)
    private Workstream workstream;

    @Column(name = "contract_reference", nullable = false, length = 128)
    private String contractReference;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "contract_category", nullable = false, length = 64)
    private String contractCategory;

    @Column(name = "counterparty_name", nullable = false, length = 255)
    private String counterpartyName;

    @Column(name = "is_related_party", nullable = false)
    private boolean relatedParty = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_party_id")
    private RelatedParty relatedPartyEntity;

    @Column(name = "execution_date", nullable = false)
    private LocalDate executionDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "is_perpetual", nullable = false)
    private boolean perpetual = false;

    @Column(name = "contract_value_annual", precision = 18, scale = 2)
    private BigDecimal contractValueAnnual;

    @Column(name = "contract_value_total", precision = 18, scale = 2)
    private BigDecimal contractValueTotal;

    @Column(name = "revenue_percentage", precision = 6, scale = 2)
    private BigDecimal revenuePercentage;

    @Column(name = "supply_percentage", precision = 6, scale = 2)
    private BigDecimal supplyPercentage;

    @Column(name = "is_above_10_pct_screening_indicator", nullable = false)
    private boolean above10PctScreeningIndicator = false;

    @Column(name = "has_change_of_control", nullable = false)
    private boolean hasChangeOfControl = false;

    @Column(name = "change_of_control_summary", columnDefinition = "TEXT")
    private String changeOfControlSummary;

    @Column(name = "change_of_control_consent_obtained", nullable = false)
    private boolean changeOfControlConsentObtained = false;

    @Column(name = "has_exclusivity", nullable = false)
    private boolean hasExclusivity = false;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE"; // ACTIVE, EXPIRING_SOON, EXPIRED, TERMINATED, UNDER_AMENDMENT

    @Column(name = "review_status", nullable = false, length = 32)
    private String reviewStatus = "PENDING"; // PENDING, REVIEWED, REQUIRES_AMENDMENT, CONSENT_PENDING

    protected MaterialContract() {
    }

    public MaterialContract(Transaction transaction, Workstream workstream, String contractReference,
                            String title, String contractCategory, String counterpartyName,
                            boolean relatedParty, RelatedParty relatedPartyEntity,
                            LocalDate executionDate, LocalDate effectiveDate, LocalDate expiryDate,
                            boolean perpetual, BigDecimal contractValueAnnual, BigDecimal contractValueTotal,
                            BigDecimal revenuePercentage, BigDecimal supplyPercentage,
                            boolean above10PctScreeningIndicator, boolean hasChangeOfControl,
                            String changeOfControlSummary, boolean changeOfControlConsentObtained,
                            boolean hasExclusivity, String status, String reviewStatus) {
        this.transaction = transaction;
        this.workstream = workstream;
        this.contractReference = contractReference;
        this.title = title;
        this.contractCategory = contractCategory;
        this.counterpartyName = counterpartyName;
        this.relatedParty = relatedParty;
        this.relatedPartyEntity = relatedPartyEntity;
        this.executionDate = executionDate;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.perpetual = perpetual;
        this.contractValueAnnual = contractValueAnnual;
        this.contractValueTotal = contractValueTotal;
        this.revenuePercentage = revenuePercentage;
        this.supplyPercentage = supplyPercentage;
        this.above10PctScreeningIndicator = above10PctScreeningIndicator;
        this.hasChangeOfControl = hasChangeOfControl;
        this.changeOfControlSummary = changeOfControlSummary;
        this.changeOfControlConsentObtained = changeOfControlConsentObtained;
        this.hasExclusivity = hasExclusivity;
        this.status = status != null ? status : "ACTIVE";
        this.reviewStatus = reviewStatus != null ? reviewStatus : "PENDING";
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Workstream getWorkstream() {
        return workstream;
    }

    public void setWorkstream(Workstream workstream) {
        this.workstream = workstream;
    }

    public String getContractReference() {
        return contractReference;
    }

    public void setContractReference(String contractReference) {
        this.contractReference = contractReference;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContractCategory() {
        return contractCategory;
    }

    public void setContractCategory(String contractCategory) {
        this.contractCategory = contractCategory;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public void setCounterpartyName(String counterpartyName) {
        this.counterpartyName = counterpartyName;
    }

    public boolean isRelatedParty() {
        return relatedParty;
    }

    public void setRelatedParty(boolean relatedParty) {
        this.relatedParty = relatedParty;
    }

    public RelatedParty getRelatedPartyEntity() {
        return relatedPartyEntity;
    }

    public void setRelatedPartyEntity(RelatedParty relatedPartyEntity) {
        this.relatedPartyEntity = relatedPartyEntity;
    }

    public LocalDate getExecutionDate() {
        return executionDate;
    }

    public void setExecutionDate(LocalDate executionDate) {
        this.executionDate = executionDate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isPerpetual() {
        return perpetual;
    }

    public void setPerpetual(boolean perpetual) {
        this.perpetual = perpetual;
    }

    public BigDecimal getContractValueAnnual() {
        return contractValueAnnual;
    }

    public void setContractValueAnnual(BigDecimal contractValueAnnual) {
        this.contractValueAnnual = contractValueAnnual;
    }

    public BigDecimal getContractValueTotal() {
        return contractValueTotal;
    }

    public void setContractValueTotal(BigDecimal contractValueTotal) {
        this.contractValueTotal = contractValueTotal;
    }

    public BigDecimal getRevenuePercentage() {
        return revenuePercentage;
    }

    public void setRevenuePercentage(BigDecimal revenuePercentage) {
        this.revenuePercentage = revenuePercentage;
    }

    public BigDecimal getSupplyPercentage() {
        return supplyPercentage;
    }

    public void setSupplyPercentage(BigDecimal supplyPercentage) {
        this.supplyPercentage = supplyPercentage;
    }

    public boolean isAbove10PctScreeningIndicator() {
        return above10PctScreeningIndicator;
    }

    public void setAbove10PctScreeningIndicator(boolean above10PctScreeningIndicator) {
        this.above10PctScreeningIndicator = above10PctScreeningIndicator;
    }

    public boolean isHasChangeOfControl() {
        return hasChangeOfControl;
    }

    public void setHasChangeOfControl(boolean hasChangeOfControl) {
        this.hasChangeOfControl = hasChangeOfControl;
    }

    public String getChangeOfControlSummary() {
        return changeOfControlSummary;
    }

    public void setChangeOfControlSummary(String changeOfControlSummary) {
        this.changeOfControlSummary = changeOfControlSummary;
    }

    public boolean isChangeOfControlConsentObtained() {
        return changeOfControlConsentObtained;
    }

    public void setChangeOfControlConsentObtained(boolean changeOfControlConsentObtained) {
        this.changeOfControlConsentObtained = changeOfControlConsentObtained;
    }

    public boolean isHasExclusivity() {
        return hasExclusivity;
    }

    public void setHasExclusivity(boolean hasExclusivity) {
        this.hasExclusivity = hasExclusivity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }
}
