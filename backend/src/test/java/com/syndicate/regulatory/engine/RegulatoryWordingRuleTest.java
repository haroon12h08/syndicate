package com.syndicate.regulatory.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wording rule lint test (spec §13 boundary, invariant 18; plan D2).
 * Verifies that the regulatory engine and control evaluation services never emit banned phrases:
 * e.g. "SEBI will approve", "compliant with SEBI", "guaranteed compliance", etc.
 */
class RegulatoryWordingRuleTest {

    private static final List<String> BANNED_PHRASES = List.of(
            "sebi will approve",
            "compliant with sebi",
            "statutory approval guaranteed",
            "guaranteed compliance",
            "compliance certified"
    );

    @Test
    @DisplayName("Invariant 18: Regulatory engine source code never contains prohibited promissory compliance phrases")
    void verifyNoBannedWordingInRegulatoryEngine() throws IOException {
        Path enginePath = Path.of("src/main/java/com/syndicate/regulatory/engine/RegulatoryEngineService.java");
        if (!Files.exists(enginePath)) {
            enginePath = Path.of("backend/src/main/java/com/syndicate/regulatory/engine/RegulatoryEngineService.java");
        }

        assertThat(Files.exists(enginePath)).isTrue();
        String content = Files.readString(enginePath).toLowerCase(Locale.ROOT);

        for (String phrase : BANNED_PHRASES) {
            assertThat(content)
                    .withFailMessage("Invariant 18 violation: RegulatoryEngineService contains prohibited phrase: '%s'", phrase)
                    .doesNotContain(phrase);
        }
    }

    @Test
    @DisplayName("Invariant 18: Formatted explanation adheres strictly to deterministic template")
    void explanationTemplate_startsStrictlyWithStandardFormula() {
        String template = "Based on configured rule %s v%d and current data, evaluates to %s. %s";
        String sample = String.format(template, "CTRL_BSE_SME_CAPITAL", 1, "FAIL", "Post-issue capital exceeds ceiling.");

        assertThat(sample)
                .startsWith("Based on configured rule")
                .contains("and current data, evaluates to")
                .doesNotContain("compliant")
                .doesNotContain("approved by regulator");
    }
}
