package com.syndicate.fact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalog entry for a semantic fact key (plan §2.1). Seeded by migration; read-only at runtime. */
@Entity
@Table(name = "fact_definitions")
public class FactDefinition {

    @Id
    @Column(name = "fact_key")
    private String factKey;

    @Column(name = "display_label", nullable = false)
    private String displayLabel;

    @Column(name = "value_type", nullable = false)
    private String valueType;

    @Column(name = "is_financial", nullable = false)
    private boolean financial;

    @Column(name = "default_materiality", nullable = false)
    private String defaultMateriality;

    protected FactDefinition() {
    }

    public String getFactKey() {
        return factKey;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getValueType() {
        return valueType;
    }

    public boolean isFinancial() {
        return financial;
    }

    public String getDefaultMateriality() {
        return defaultMateriality;
    }
}
