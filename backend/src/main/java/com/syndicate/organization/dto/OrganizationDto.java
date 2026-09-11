package com.syndicate.organization.dto;

import com.syndicate.organization.Organization;
import com.syndicate.organization.OrganizationType;

import java.util.UUID;

public record OrganizationDto(UUID id, String name, OrganizationType type) {
    public static OrganizationDto from(Organization organization) {
        return new OrganizationDto(organization.getId(), organization.getName(), organization.getType());
    }
}
