package com.syndicate.transaction;

import com.syndicate.common.BaseEntity;
import com.syndicate.company.Company;
import com.syndicate.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_organization_id", nullable = false)
    private Organization leadOrganization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    protected Transaction() {
    }

    @Column(name = "estimated_filing_date")
    private LocalDate estimatedFilingDate;

    public Transaction(Company company, Organization leadOrganization, TransactionType type, String name) {
        this.company = company;
        this.leadOrganization = leadOrganization;
        this.type = type;
        this.name = name;
        this.status = TransactionStatus.DRAFT;
    }

    public Company getCompany() {
        return company;
    }

    public Organization getLeadOrganization() {
        return leadOrganization;
    }

    public TransactionType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public LocalDate getEstimatedFilingDate() {
        return estimatedFilingDate;
    }

    public void setEstimatedFilingDate(LocalDate estimatedFilingDate) {
        this.estimatedFilingDate = estimatedFilingDate;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
