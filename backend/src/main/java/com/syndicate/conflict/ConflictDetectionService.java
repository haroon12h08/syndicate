package com.syndicate.conflict;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.issue.Issue;
import com.syndicate.issue.IssueRepository;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.issue.IssueStatus;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Detects contradictory representations of the same fact (spec §18).
 *
 * <p>When two current facts share a label and period but disagree on value, the system raises an
 * Issue naming every competing value and its source document. It deliberately does not pick a
 * winner — choosing between conflicting representations is a human judgement, and silently
 * selecting one would be exactly the failure this feature exists to prevent.
 */
@Service
public class ConflictDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ConflictDetectionService.class);

    private final FactRepository factRepository;
    private final IssueRepository issueRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public ConflictDetectionService(FactRepository factRepository, IssueRepository issueRepository,
                                     TransactionRepository transactionRepository, AuditService auditService) {
        this.factRepository = factRepository;
        this.issueRepository = issueRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    /**
     * Runs after a fact change commits, in its own transaction, for the same reason readiness
     * re-evaluation does: joining a completing transaction would discard these writes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void detectQuietly(UUID transactionId, User caller) {
        try {
            detect(transactionId, caller);
        } catch (Exception e) {
            log.warn("Conflict detection failed for transaction {}: {}", transactionId, e.toString());
        }
    }

    private void detect(UUID transactionId, User caller) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null) {
            return;
        }

        List<Fact> current = factRepository.findByWorkstreamTransactionId(transactionId).stream()
                .filter(f -> f.getStatus() != FactStatus.SUPERSEDED)
                .toList();

        Map<String, List<Fact>> grouped = new LinkedHashMap<>();
        for (Fact fact : current) {
            grouped.computeIfAbsent(conflictKey(fact), k -> new ArrayList<>()).add(fact);
        }

        for (Map.Entry<String, List<Fact>> entry : grouped.entrySet()) {
            List<Fact> facts = entry.getValue();
            Set<String> distinctValues = facts.stream()
                    .map(f -> normaliseValue(f.getValue()))
                    .collect(Collectors.toCollection(TreeSet::new));

            Issue existing = issueRepository.findFirstByConflictKey(entry.getKey()).orElse(null);

            if (distinctValues.size() > 1) {
                raiseOrRefresh(transaction, entry.getKey(), facts, distinctValues, existing, caller);
            } else if (existing != null && existing.getStatus() != IssueStatus.RESOLVED
                    && existing.getStatus() != IssueStatus.CLOSED) {
                existing.autoResolve("Values now agree: " + String.join(", ", distinctValues));
                auditService.record(transactionId, caller, AuditAction.CONFLICT_CLEARED, "Issue",
                        existing.getId(), "Conflict resolved for " + facts.get(0).getLabel());
            }
        }
    }

    private void raiseOrRefresh(Transaction transaction, String conflictKey, List<Fact> facts,
                                 Set<String> distinctValues, Issue existing, User caller) {
        Fact sample = facts.get(0);
        String title = "Conflicting values for \"" + sample.getLabel() + "\""
                + (sample.getPeriod() != null ? " (" + sample.getPeriod() + ")" : "");
        String description = buildDescription(facts, distinctValues);

        if (existing != null) {
            existing.reopen(description, IssueSeverity.HIGH);
            existing.getRelatedFacts().clear();
            existing.getRelatedFacts().addAll(facts);
            return;
        }

        Workstream workstream = facts.stream()
                .map(Fact::getWorkstream)
                .min(Comparator.comparing(w -> w.getType().name()))
                .orElse(sample.getWorkstream());

        Issue issue = new Issue(workstream, title, description, IssueSeverity.HIGH, null, null,
                caller != null ? caller : sample.getCreatedByUser());
        issue.setConflictKey(conflictKey);
        issue.getRelatedFacts().addAll(facts);
        facts.forEach(f -> issue.getRelatedEvidence().addAll(f.getEvidence()));
        Issue saved = issueRepository.save(issue);

        auditService.record(transaction.getId(), caller, AuditAction.CONFLICT_DETECTED, "Issue",
                saved.getId(), title, null, String.join(" | ", distinctValues), null);
        log.info("Conflict detected on transaction {}: {} -> {}", transaction.getId(), conflictKey, distinctValues);
    }

    /** Lists every competing value with the document it came from, so a reviewer can adjudicate. */
    private String buildDescription(List<Fact> facts, Set<String> distinctValues) {
        StringBuilder sb = new StringBuilder("Sources disagree on this value. No value has been selected automatically.\n");
        for (Fact fact : facts) {
            sb.append("\n• ").append(fact.getValue());
            if (fact.getUnit() != null) {
                sb.append(' ').append(fact.getUnit());
            }
            sb.append(" — ").append(fact.getStatus().name())
                    .append(", ").append(fact.getWorkstream().getType().name());
            if (!fact.getEvidence().isEmpty()) {
                sb.append(", from ").append(fact.getEvidence().stream()
                        .map(e -> e.getFileName())
                        .collect(Collectors.joining(", ")));
            } else {
                sb.append(", entered directly");
            }
        }
        sb.append("\n\nResolve by superseding the incorrect facts so a single value remains.");
        String text = sb.toString();
        return text.length() > 1000 ? text.substring(0, 997) + "..." : text;
    }

    /** Facts collide only when they describe the same thing for the same period. */
    private String conflictKey(Fact fact) {
        String label = fact.getLabel().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String period = fact.getPeriod() == null ? "" : fact.getPeriod().trim().toLowerCase(Locale.ROOT);
        return fact.getWorkstream().getTransaction().getId() + "::" + label + "::" + period;
    }

    private String normaliseValue(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().replaceAll("[,\\s]", "").toLowerCase(Locale.ROOT);
        try {
            // 51.0 and 51 are the same figure; only real disagreements should raise an issue.
            return new java.math.BigDecimal(cleaned).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return cleaned;
        }
    }
}
