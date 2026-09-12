package com.syndicate.drhp.dto;

import com.syndicate.drhp.DisclosureStatus;
import jakarta.validation.constraints.NotBlank;

public record SaveDisclosureRequest(
        @NotBlank String sectionCode,
        @NotBlank String title,
        @NotBlank String bodyTemplate,
        DisclosureStatus status,
        Integer orderIndex
) {
}
