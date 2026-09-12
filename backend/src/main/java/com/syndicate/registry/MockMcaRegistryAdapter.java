package com.syndicate.registry;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Stands in for the MCA CIN/DIN lookup until a real gateway is wired up.
 *
 * <p>The returned figures are derived deterministically from the CIN itself rather than randomly:
 * the same CIN always yields the same record, so registry-mismatch behaviour is reproducible in
 * tests and demos instead of flapping between runs.
 *
 * <p>Indian CINs encode the incorporation year at positions 9-12 (e.g. U12345MH<b>2020</b>PLC000111),
 * which is parsed here so the date comparison exercises real data rather than a constant.
 */
@Component
public class MockMcaRegistryAdapter implements ExternalRegistryService {

    private static final String SOURCE = "MCA_MOCK";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Optional<RegistryRecord> lookup(String cin, String pan) {
        String key = (cin != null && !cin.isBlank()) ? cin.trim().toUpperCase(Locale.ROOT)
                : (pan != null ? pan.trim().toUpperCase(Locale.ROOT) : null);
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        // A CIN the registry has never heard of is a real outcome worth modelling.
        if (key.startsWith("UNKNOWN")) {
            return Optional.empty();
        }

        Random seeded = new Random(key.hashCode());

        BigDecimal authorized = BigDecimal.valueOf(50_000_000L + seeded.nextInt(40) * 5_000_000L)
                .setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal paidUp = authorized
                .multiply(BigDecimal.valueOf(60 + seeded.nextInt(35)))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return Optional.of(new RegistryRecord(
                SOURCE,
                key,
                registryLegalName(key),
                incorporationDateFrom(key, seeded),
                authorized,
                paidUp,
                "Registered Office on file with MCA, " + stateFrom(key),
                "ACTIVE"
        ));
    }

    /**
     * The registry holds the statutory name, which frequently differs in punctuation and suffix
     * from what a deal team types — exactly the kind of mismatch worth surfacing.
     */
    private String registryLegalName(String key) {
        return "VERTEX INDUSTRIES PRIVATE LIMITED".equals(key)
                ? key
                : "REGISTERED ENTITY " + key.substring(0, Math.min(12, key.length()));
    }

    private LocalDate incorporationDateFrom(String cin, Random seeded) {
        int year = 2015 + seeded.nextInt(8);
        if (cin.length() >= 12) {
            try {
                int parsed = Integer.parseInt(cin.substring(8, 12));
                if (parsed >= 1900 && parsed <= LocalDate.now().getYear()) {
                    year = parsed;
                }
            } catch (NumberFormatException ignored) {
                // Not a conventional CIN; the seeded year stands.
            }
        }
        return LocalDate.of(year, 1 + seeded.nextInt(12), 1 + seeded.nextInt(28));
    }

    private String stateFrom(String cin) {
        return cin.length() >= 8 ? cin.substring(5, 7) : "IN";
    }
}
