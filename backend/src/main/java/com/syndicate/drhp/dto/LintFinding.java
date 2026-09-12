package com.syndicate.drhp.dto;

/** A reason the DRHP cannot be compiled. Every finding blocks. */
public record LintFinding(
        String code,
        String sectionCode,
        String factLabel,
        String message
) {
}
