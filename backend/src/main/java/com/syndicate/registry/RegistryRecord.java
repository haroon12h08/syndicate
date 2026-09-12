package com.syndicate.registry;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What an external corporate registry returns about a company. */
public record RegistryRecord(
        String source,
        String lookupKey,
        String legalName,
        LocalDate incorporationDate,
        BigDecimal authorizedCapital,
        BigDecimal paidUpCapital,
        String registeredOffice,
        String companyStatus
) {
    /**
     * Stable field ordering so the same record always hashes the same way — the snapshot hash is
     * only meaningful if it is reproducible.
     */
    public String canonical() {
        return String.join("|",
                source, lookupKey,
                String.valueOf(legalName),
                String.valueOf(incorporationDate),
                String.valueOf(authorizedCapital),
                String.valueOf(paidUpCapital),
                String.valueOf(registeredOffice),
                String.valueOf(companyStatus));
    }
}
