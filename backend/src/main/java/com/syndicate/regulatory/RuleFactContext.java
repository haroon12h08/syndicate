package com.syndicate.regulatory;

import com.syndicate.fact.Fact;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fact universe a single rule evaluation sees: the transaction's currently-believed,
 * human-verified facts. Draft facts are deliberately excluded — an unverified extraction must
 * never move a regulatory verdict on its own.
 *
 * <p>Every lookup records which facts it touched, so an evaluation can report the exact
 * evidence behind its verdict.
 */
public class RuleFactContext {

    private final List<Fact> facts;
    private final Map<String, Double> variables;
    private final Set<Fact> touchedFacts = new LinkedHashSet<>();

    public RuleFactContext(List<Fact> facts, Map<String, Double> variables) {
        this.facts = facts;
        this.variables = variables;
    }

    public Double variable(String name) {
        return variables.get(name);
    }

    public List<Fact> matching(String labelPattern) {
        String needle = labelPattern.toLowerCase();
        return facts.stream()
                .filter(f -> f.getLabel().toLowerCase().contains(needle))
                .toList();
    }

    public Double factValue(String labelPattern) {
        List<Fact> matches = matching(labelPattern);
        touchedFacts.addAll(matches);
        return matches.stream()
                .map(this::numericValue)
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    public int factCount(String labelPattern) {
        List<Fact> matches = matching(labelPattern);
        touchedFacts.addAll(matches);
        return matches.size();
    }

    public int countFactsAbove(String labelPattern, double threshold) {
        List<Fact> matches = matching(labelPattern);
        touchedFacts.addAll(matches);
        return (int) matches.stream()
                .map(this::numericValue)
                .filter(v -> v != null && v > threshold)
                .count();
    }

    /**
     * Earliest business-validity end among matching facts. A fact with no {@code validTo} is
     * still true today, so it never constrains the minimum.
     */
    public Double minValidToEpochMillis(String labelPattern) {
        List<Fact> matches = matching(labelPattern);
        touchedFacts.addAll(matches);
        if (matches.isEmpty()) {
            return null;
        }
        double earliest = Double.MAX_VALUE;
        for (Fact fact : matches) {
            Instant validTo = fact.getValidTo();
            double value = validTo == null ? Double.MAX_VALUE : validTo.toEpochMilli();
            earliest = Math.min(earliest, value);
        }
        return earliest;
    }

    /**
     * Parses a fact's free-text value into a number, tolerating the separators and currency
     * marks people actually type ("1,20,00,000", "INR 4.2 Cr" -> 4.2 is not assumed; only the
     * numeric portion is read).
     */
    private Double numericValue(Fact fact) {
        String raw = fact.getValue();
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[,\\s]", "").replaceAll("(?i)^(inr|rs\\.?|₹)", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Set<Fact> touchedFacts() {
        return touchedFacts;
    }
}
