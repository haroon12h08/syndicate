package com.syndicate.company.dto;

import com.syndicate.company.CompanyConstitution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCompanyRequest(
        @NotBlank String legalName,
        String cin,
        String pan,
        String registeredOffice,
        LocalDate incorporationDate,
        CompanyConstitution constitution,
        @NotNull UUID ownerOrganizationId
) {
}
