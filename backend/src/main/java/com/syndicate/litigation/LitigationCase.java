package com.syndicate.litigation;

import com.syndicate.common.BaseEntity;
import com.syndicate.transaction.Transaction;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "litigation_cases")
public class LitigationCase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workstream_id", nullable = false)
    private Workstream workstream;

    @Column(name = "case_number", nullable = false, length = 128)
    private String caseNumber;

    @Column(name = "court_forum", nullable = false, length = 255)
    private String courtForum;

    @Column(name = "jurisdiction_city", length = 128)
    private String jurisdictionCity;

    @Column(name = "case_title", nullable = false, length = 512)
    private String caseTitle;

    @Column(nullable = false, length = 64)
    private String category; // CRIMINAL_PROCEEDING, REGULATORY_STATUTORY_ACTION, etc.

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType; // ISSUER, PROMOTER, DIRECTOR, SUBSIDIARY, GROUP_COMPANY

    @Column(name = "entity_name", nullable = false, length = 255)
    private String entityName;

    @Column(name = "party_role", nullable = false, length = 32)
    private String partyRole; // PETITIONER_PLAINTIFF, RESPONDENT_DEFENDANT, APPELLANT

    @Column(name = "opposite_party", nullable = false, length = 255)
    private String oppositeParty;

    @Column(name = "filing_date")
    private LocalDate filingDate;

    @Column(name = "claimed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal claimedAmount = BigDecimal.ZERO;

    @Column(name = "contingent_liability_exposure", nullable = false, precision = 18, scale = 2)
    private BigDecimal contingentLiabilityExposure = BigDecimal.ZERO;

    @Column(name = "provision_made", precision = 18, scale = 2)
    private BigDecimal provisionMade = BigDecimal.ZERO;

    @Column(name = "potential_impact", nullable = false, length = 32)
    private String potentialImpact; // FINANCIAL, OPERATIONAL, REPUTATIONAL, MANAGEMENT_DISQUALIFICATION, PERMIT_REVOCATION

    @Column(name = "case_summary", nullable = false, columnDefinition = "TEXT")
    private String caseSummary;

    @Column(nullable = false, length = 32)
    private String status = "PENDING"; // PENDING, STAYED, APPEAL_PREFERRED, DISPOSED_FAVORABLE, DISPOSED_UNFAVORABLE, SETTLED

    @Column(name = "is_material", nullable = false)
    private boolean material = false;

    @Column(name = "materiality_basis", length = 128)
    private String materialityBasis;

    protected LitigationCase() {
    }

    public LitigationCase(Transaction transaction, Workstream workstream, String caseNumber,
                          String courtForum, String jurisdictionCity, String caseTitle,
                          String category, String entityType, String entityName,
                          String partyRole, String oppositeParty, LocalDate filingDate,
                          BigDecimal claimedAmount, BigDecimal contingentLiabilityExposure,
                          BigDecimal provisionMade, String potentialImpact, String caseSummary,
                          String status, boolean material, String materialityBasis) {
        this.transaction = transaction;
        this.workstream = workstream;
        this.caseNumber = caseNumber;
        this.courtForum = courtForum;
        this.jurisdictionCity = jurisdictionCity;
        this.caseTitle = caseTitle;
        this.category = category;
        this.entityType = entityType;
        this.entityName = entityName;
        this.partyRole = partyRole;
        this.oppositeParty = oppositeParty;
        this.filingDate = filingDate;
        this.claimedAmount = claimedAmount != null ? claimedAmount : BigDecimal.ZERO;
        this.contingentLiabilityExposure = contingentLiabilityExposure != null ? contingentLiabilityExposure : BigDecimal.ZERO;
        this.provisionMade = provisionMade != null ? provisionMade : BigDecimal.ZERO;
        this.potentialImpact = potentialImpact;
        this.caseSummary = caseSummary;
        this.status = status != null ? status : "PENDING";
        this.material = material;
        this.materialityBasis = materialityBasis;
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

    public String getCaseNumber() {
        return caseNumber;
    }

    public void setCaseNumber(String caseNumber) {
        this.caseNumber = caseNumber;
    }

    public String getCourtForum() {
        return courtForum;
    }

    public void setCourtForum(String courtForum) {
        this.courtForum = courtForum;
    }

    public String getJurisdictionCity() {
        return jurisdictionCity;
    }

    public void setJurisdictionCity(String jurisdictionCity) {
        this.jurisdictionCity = jurisdictionCity;
    }

    public String getCaseTitle() {
        return caseTitle;
    }

    public void setCaseTitle(String caseTitle) {
        this.caseTitle = caseTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getPartyRole() {
        return partyRole;
    }

    public void setPartyRole(String partyRole) {
        this.partyRole = partyRole;
    }

    public String getOppositeParty() {
        return oppositeParty;
    }

    public void setOppositeParty(String oppositeParty) {
        this.oppositeParty = oppositeParty;
    }

    public LocalDate getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(LocalDate filingDate) {
        this.filingDate = filingDate;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public void setClaimedAmount(BigDecimal claimedAmount) {
        this.claimedAmount = claimedAmount;
    }

    public BigDecimal getContingentLiabilityExposure() {
        return contingentLiabilityExposure;
    }

    public void setContingentLiabilityExposure(BigDecimal contingentLiabilityExposure) {
        this.contingentLiabilityExposure = contingentLiabilityExposure;
    }

    public BigDecimal getProvisionMade() {
        return provisionMade;
    }

    public void setProvisionMade(BigDecimal provisionMade) {
        this.provisionMade = provisionMade;
    }

    public String getPotentialImpact() {
        return potentialImpact;
    }

    public void setPotentialImpact(String potentialImpact) {
        this.potentialImpact = potentialImpact;
    }

    public String getCaseSummary() {
        return caseSummary;
    }

    public void setCaseSummary(String caseSummary) {
        this.caseSummary = caseSummary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isMaterial() {
        return material;
    }

    public void setMaterial(boolean material) {
        this.material = material;
    }

    public String getMaterialityBasis() {
        return materialityBasis;
    }

    public void setMaterialityBasis(String materialityBasis) {
        this.materialityBasis = materialityBasis;
    }
}
