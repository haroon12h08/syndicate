package com.syndicate.company;

import com.syndicate.common.BaseEntity;
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
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "cin")
    private String cin;

    @Column(name = "pan")
    private String pan;

    @Column(name = "registered_office")
    private String registeredOffice;

    @Column(name = "incorporation_date")
    private LocalDate incorporationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "constitution")
    private CompanyConstitution constitution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_organization_id", nullable = false)
    private Organization ownerOrganization;

    protected Company() {
    }

    public Company(String legalName, String cin, String pan, String registeredOffice,
                    LocalDate incorporationDate, CompanyConstitution constitution, Organization ownerOrganization) {
        this.legalName = legalName;
        this.cin = cin;
        this.pan = pan;
        this.registeredOffice = registeredOffice;
        this.incorporationDate = incorporationDate;
        this.constitution = constitution;
        this.ownerOrganization = ownerOrganization;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getCin() {
        return cin;
    }

    public String getPan() {
        return pan;
    }

    public String getRegisteredOffice() {
        return registeredOffice;
    }

    public LocalDate getIncorporationDate() {
        return incorporationDate;
    }

    public CompanyConstitution getConstitution() {
        return constitution;
    }

    public Organization getOwnerOrganization() {
        return ownerOrganization;
    }

    public void update(String legalName, String cin, String pan, String registeredOffice,
                        LocalDate incorporationDate, CompanyConstitution constitution) {
        this.legalName = legalName;
        this.cin = cin;
        this.pan = pan;
        this.registeredOffice = registeredOffice;
        this.incorporationDate = incorporationDate;
        this.constitution = constitution;
    }
}
