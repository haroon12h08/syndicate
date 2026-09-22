package com.syndicate.fact;

import org.junit.jupiter.api.Test;

import static com.syndicate.fact.FactValueNormalizer.canonical;
import static org.assertj.core.api.Assertions.assertThat;

class FactValueNormalizerTest {

    @Test
    void indianScalesCompareEqual() {
        String crore = canonical("42.18", "INR crore");
        assertThat(canonical("₹4,218 lakh", null)).isEqualTo(crore);
        assertThat(canonical("42,18,00,000", "INR")).isEqualTo(crore);
        assertThat(canonical("Rs. 42.18 Cr", null)).isEqualTo(crore);
    }

    @Test
    void genuineDifferencesStayDifferent() {
        assertThat(canonical("42.18", "INR crore")).isNotEqualTo(canonical("41.80", "INR crore"));
        assertThat(canonical("51", "%")).isNotEqualTo(canonical("49%", null));
    }

    @Test
    void trailingZerosAndTextAreNormalised() {
        assertThat(canonical("51.0", null)).isEqualTo(canonical("51", null));
        assertThat(canonical("  Pending  Appeal ", null)).isEqualTo("pending appeal");
    }
}
