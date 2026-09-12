package com.syndicate.registry;

import com.syndicate.common.BaseEntity;
import com.syndicate.company.Company;
import com.syndicate.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** An immutable point-in-time copy of what the registry said, kept as evidence for the comparison. */
@Entity
@Table(name = "registry_snapshots")
public class RegistrySnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String source;

    @Column(name = "lookup_key", nullable = false)
    private String lookupKey;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "incorporation_date")
    private LocalDate incorporationDate;

    @Column(name = "authorized_capital")
    private BigDecimal authorizedCapital;

    @Column(name = "paid_up_capital")
    private BigDecimal paidUpCapital;

    @Column(name = "registered_office")
    private String registeredOffice;

    @Column(name = "company_status")
    private String companyStatus;

    @Column(name = "payload_sha256", nullable = false)
    private String payloadSha256;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fetched_by_user_id")
    private User fetchedByUser;

    protected RegistrySnapshot() {
    }

    public RegistrySnapshot(Company company, RegistryRecord record, String payloadSha256, User fetchedBy) {
        this.company = company;
        this.source = record.source();
        this.lookupKey = record.lookupKey();
        this.legalName = record.legalName();
        this.incorporationDate = record.incorporationDate();
        this.authorizedCapital = record.authorizedCapital();
        this.paidUpCapital = record.paidUpCapital();
        this.registeredOffice = record.registeredOffice();
        this.companyStatus = record.companyStatus();
        this.payloadSha256 = payloadSha256;
        this.rawPayload = record.canonical();
        this.fetchedAt = Instant.now();
        this.fetchedByUser = fetchedBy;
    }

    public Company getCompany() {
        return company;
    }

    public String getSource() {
        return source;
    }

    public String getLookupKey() {
        return lookupKey;
    }

    public String getLegalName() {
        return legalName;
    }

    public LocalDate getIncorporationDate() {
        return incorporationDate;
    }

    public BigDecimal getAuthorizedCapital() {
        return authorizedCapital;
    }

    public BigDecimal getPaidUpCapital() {
        return paidUpCapital;
    }

    public String getRegisteredOffice() {
        return registeredOffice;
    }

    public String getCompanyStatus() {
        return companyStatus;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public User getFetchedByUser() {
        return fetchedByUser;
    }
}
