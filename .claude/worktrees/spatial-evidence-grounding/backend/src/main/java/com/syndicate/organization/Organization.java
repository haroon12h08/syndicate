package com.syndicate.organization;

import com.syndicate.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationType type;

    protected Organization() {
    }

    public Organization(String name, OrganizationType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public OrganizationType getType() {
        return type;
    }

    public void setName(String name) {
        this.name = name;
    }
}
