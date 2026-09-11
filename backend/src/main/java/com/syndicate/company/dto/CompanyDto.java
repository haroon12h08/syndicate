package com.syndicate.company.dto;

import com.syndicate.company.Company;
import com.syndicate.company.CompanyConstitution;

import java.time.LocalDate;
import java.util.UUID;

public record CompanyDto(
        UUID id,
        String legalName,
        String cin,
        String pan,
        String registeredOffice,
        LocalDate incorporationDate,
        CompanyConstitution constitution,
        UUID ownerOrganizationId,
        String ownerOrganizationName
) {
    public static CompanyDto from(Company company) {
        return new CompanyDto(
                company.getId(),
                company.getLegalName(),
                company.getCin(),
                company.getPan(),
                company.getRegisteredOffice(),
                company.getIncorporationDate(),
                company.getConstitution(),
                company.getOwnerOrganization().getId(),
                company.getOwnerOrganization().getName()
        );
    }
}
