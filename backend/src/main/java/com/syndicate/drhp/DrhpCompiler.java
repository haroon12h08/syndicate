package com.syndicate.drhp;

import com.syndicate.fact.Fact;
import com.syndicate.fact.FactStatus;
import com.syndicate.drhp.dto.CompiledSection;
import com.syndicate.drhp.dto.CompiledSegment;
import com.syndicate.drhp.dto.LintFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders disclosures into DRHP sections, resolving {@code {{fact:<key>}}} placeholders (optionally
 * {@code {{fact:<key>@<period>}}}) against the transaction's facts.
 *
 * <p>This is where Zero AI Authority cashes out at document level: a placeholder only resolves
 * against a VERIFIED fact. A value that came from an extraction and was never accepted by a human
 * is still a CandidateFact, has no Fact row, and therefore cannot resolve — the compile fails
 * rather than printing an unreviewed number into a regulatory filing.
 */
@Component
public class DrhpCompiler {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{fact:([^}@]+)(?:@([^}]+))?}}");

    /** Every placeholder in a template, as written. */
    public static List<Reference> references(String template) {
        List<Reference> references = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template == null ? "" : template);
        while (matcher.find()) {
            references.add(new Reference(matcher.group(1).trim(),
                    matcher.group(2) == null ? null : matcher.group(2).trim()));
        }
        return references;
    }

    public record Reference(String factKey, String period) {
        public String display() {
            return period == null ? factKey : factKey + "@" + period;
        }
    }

    public record CompileResult(List<CompiledSection> sections, List<LintFinding> findings, Set<Fact> citedFacts) {
        public boolean clean() {
            return findings.isEmpty();
        }
    }

    public CompileResult compile(List<Disclosure> disclosures, List<Fact> transactionFacts) {
        List<CompiledSection> sections = new ArrayList<>();
        List<LintFinding> findings = new ArrayList<>();
        Set<Fact> cited = new LinkedHashSet<>();

        if (disclosures.isEmpty()) {
            findings.add(new LintFinding("NO_DISCLOSURES", null, null,
                    "This transaction has no disclosure sections to compile."));
        }

        for (Disclosure disclosure : disclosures) {
            if (disclosure.getStatus() == DisclosureStatus.STALE_REQUIRING_REVIEW) {
                findings.add(new LintFinding("STALE_DISCLOSURE", disclosure.getSectionCode(), null,
                        "\"" + disclosure.getTitle() + "\" is marked stale: "
                                + (disclosure.getStaleReason() != null ? disclosure.getStaleReason()
                                        : "a source fact changed") + ". Re-review it before compiling."));
            }
            sections.add(renderSection(disclosure, transactionFacts, findings, cited));
        }

        return new CompileResult(sections, findings, cited);
    }

    private CompiledSection renderSection(Disclosure disclosure, List<Fact> facts,
                                           List<LintFinding> findings, Set<Fact> cited) {
        List<CompiledSegment> segments = new ArrayList<>();
        String template = disclosure.getBodyTemplate();
        Matcher matcher = PLACEHOLDER.matcher(template);

        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                segments.add(CompiledSegment.text(template.substring(cursor, matcher.start())));
            }
            Reference reference = new Reference(matcher.group(1).trim(),
                    matcher.group(2) == null ? null : matcher.group(2).trim());
            segments.add(resolvePlaceholder(disclosure, reference, facts, findings, cited));
            cursor = matcher.end();
        }
        if (cursor < template.length()) {
            segments.add(CompiledSegment.text(template.substring(cursor)));
        }

        return new CompiledSection(disclosure.getId(), disclosure.getSectionCode(), disclosure.getTitle(), segments);
    }

    private CompiledSegment resolvePlaceholder(Disclosure disclosure, Reference reference, List<Fact> facts,
                                                List<LintFinding> findings, Set<Fact> cited) {
        List<Fact> matches = facts.stream()
                .filter(f -> f.getFactKey().equalsIgnoreCase(reference.factKey()))
                .filter(f -> reference.period() == null
                        || reference.period().equalsIgnoreCase(f.getPeriod()))
                .filter(f -> f.getStatus() != FactStatus.SUPERSEDED && f.getStatus() != FactStatus.REJECTED)
                .toList();

        List<Fact> verified = matches.stream()
                .filter(f -> f.getStatus() == FactStatus.VERIFIED)
                .toList();

        if (verified.isEmpty()) {
            String reason = matches.isEmpty()
                    ? "No fact " + reference.display() + " exists in this transaction."
                    : "The fact " + reference.display() + " exists but has not been verified by a human yet.";
            findings.add(new LintFinding("UNRESOLVED_FACT", disclosure.getSectionCode(), reference.display(), reason));
            return CompiledSegment.unresolved(reference.display());
        }
        if (verified.size() > 1) {
            findings.add(new LintFinding("AMBIGUOUS_FACT", disclosure.getSectionCode(), reference.display(),
                    verified.size() + " verified facts answer to " + reference.display()
                            + ". Name the period, as in {{fact:" + reference.factKey() + "@FY2026}}."));
            return CompiledSegment.unresolved(reference.display());
        }

        Fact fact = verified.get(0);
        cited.add(fact);
        return CompiledSegment.fact(fact.getId(), fact.getLabel(), fact.getValue(), fact.getUnit(),
                fact.getEvidence().stream().map(e -> e.getId()).toList());
    }
}
