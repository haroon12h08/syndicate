package com.syndicate.ai.drafting.dto;

public record DraftDisclosureRequest(
        String sectionCode,
        String title,
        String userInstructions
) {}
