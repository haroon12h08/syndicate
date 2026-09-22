package com.syndicate.fact;

import com.syndicate.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Turns what a user or extractor supplied into a stable semantic key. An explicit key must exist
 * in the catalog; a free-text label maps through the alias table, or else becomes a
 * {@code custom.<slug>} key so identical labels still share an identity.
 */
@Component
public class FactKeyResolver {

    public static final String CUSTOM_PREFIX = "custom.";

    private final FactDefinitionRepository definitions;

    public FactKeyResolver(FactDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    public String resolve(String explicitKey, String label) {
        if (explicitKey != null && !explicitKey.isBlank()) {
            String key = explicitKey.trim();
            if (!key.startsWith(CUSTOM_PREFIX) && !definitions.existsById(key)) {
                throw new BadRequestException("Unknown fact key: " + key);
            }
            return key;
        }
        String alias = normalise(label);
        return definitions.findKeyByAlias(alias).orElseGet(() -> CUSTOM_PREFIX + slug(alias));
    }

    /** Materiality a new fact starts with: the catalog default, or NORMAL for custom keys. */
    public Materiality defaultMateriality(String factKey) {
        return definitions.findById(factKey)
                .map(d -> Materiality.valueOf(d.getDefaultMateriality()))
                .orElse(Materiality.NORMAL);
    }

    static String normalise(String label) {
        return label == null ? "" : label.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String slug(String normalisedLabel) {
        return normalisedLabel.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
