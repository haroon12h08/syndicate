package com.syndicate.fact;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical comparison form for fact values. Indian financial statements mix scales freely
 * (₹42.18 crore, ₹4,218 lakh, 42,18,00,000), so numeric values are expanded to absolute units
 * using a scale found in the value itself or, failing that, in the unit. Non-numeric values
 * compare case- and whitespace-insensitively.
 */
public final class FactValueNormalizer {

    private static final Pattern NUMBER = Pattern.compile(
            "^(?:₹|rs\\.?|inr)?\\s*(-?[\\d,]*\\.?\\d+)\\s*(%|crores?|cr\\.?|lakhs?|lacs?|lac|mn|millions?|bn|billions?|thousands?|k)?$");

    private FactValueNormalizer() {
    }

    public static String canonical(String value, String unit) {
        if (value == null) {
            return "";
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        Matcher m = NUMBER.matcher(text);
        if (!m.matches()) {
            return text.replaceAll("\\s+", " ");
        }
        BigDecimal number;
        try {
            number = new BigDecimal(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return text;
        }
        BigDecimal scale = scaleOf(m.group(2));
        if (scale == null) {
            scale = scaleInUnit(unit);
        }
        return number.multiply(scale == null ? BigDecimal.ONE : scale).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal scaleInUnit(String unit) {
        if (unit == null) {
            return null;
        }
        String u = unit.toLowerCase(Locale.ROOT);
        for (String token : u.split("[^a-z%]+")) {
            BigDecimal scale = scaleOf(token);
            if (scale != null) {
                return scale;
            }
        }
        return null;
    }

    private static BigDecimal scaleOf(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return switch (token.replace(".", "")) {
            case "crore", "crores", "cr" -> new BigDecimal("10000000");
            case "lakh", "lakhs", "lac", "lacs" -> new BigDecimal("100000");
            case "mn", "million", "millions" -> new BigDecimal("1000000");
            case "bn", "billion", "billions" -> new BigDecimal("1000000000");
            case "thousand", "thousands", "k" -> new BigDecimal("1000");
            default -> null;
        };
    }
}
