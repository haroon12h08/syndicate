package com.syndicate.company.dto;

import com.syndicate.company.CompanyConstitution;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateCompanyRequest(
        @NotBlank String legalName,
        String cin,
        String pan,
        String registeredOffice,
        LocalDate incorporationDate,
        CompanyConstitution constitution
) {
}
