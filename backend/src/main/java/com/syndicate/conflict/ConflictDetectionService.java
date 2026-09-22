package com.syndicate.conflict;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.fact.FactValueNormalizer;
import com.syndicate.issue.Issue;
import com.syndicate.issue.IssueRepository;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.issue.IssueStatus;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Detects contradictory representations of the same fact (spec §11).
 *
 * <p>Two current facts conflict when they share a semantic key and period, their business
 * validity overlaps, and their values differ. Values valid over disjoint periods are a legitimate
 * succession (51% until 31 Aug, 49% from 1 Sep), not a conflict. The service never picks a winner:
 * it records an explicit {@link FactConflict} and a linked issue, and waits for a person.
 *
 * <p>It runs inside the transaction that changed a fact, so a new contradiction is on record the
 * moment the change commits.
 */
@Service
public class ConflictDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ConflictDetectionService.class);
    private static final Set<FactStatus> NOT_CURRENT = EnumSet.of(FactStatus.SUPERSEDED, FactStatus.REJECTED);

    private final FactRepository factRepository;
    private final IssueRepository issueRepository;
    private final FactConflictRepository conflictRepository;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public ConflictDetectionService(FactRepository factRepository, IssueRepository issueRepository,
                                    FactConflictRepository conflictRepository, AuditService auditService,
                                    EntityManager entityManager) {
        this.factRepository = factRepository;
        this.issueRepository = issueRepository;
        this.conflictRepository = conflictRepository;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void detect(UUID transactionId, User caller) {
        entityManager.flush();
        Map<String, List<Fact>> groups = new LinkedHashMap<>();
        for (Fact fact : factRepository.findByWorkstreamTransactionId(transactionId)) {
            if (!NOT_CURRENT.contains(fact.getStatus())) {
                groups.computeIfAbsent(conflictKey(transactionId, fact), k -> new ArrayList<>()).add(fact);
            }
        }
        for (Map.Entry<String, List<Fact>> group : groups.entrySet()) {
            Set<Fact> competing = competingFacts(group.getValue());
            FactConflict existing = conflictRepository.findByConflictKey(group.getKey()).orElse(null);
            if (!competing.isEmpty()) {
                raiseOrRefresh(transactionId, group.getKey(), competing, existing, caller);
            } else if (existing != null && existing.getStatus() == ConflictStatus.OPEN) {
                clear(existing, group.getValue(), caller);
            }
        }
        // a conflict whose facts have all been superseded or rejected has nothing left to compete
        for (FactConflict open : conflictRepository.findByTransactionIdAndStatus(transactionId, ConflictStatus.OPEN)) {
            if (!groups.containsKey(open.getConflictKey())) {
                clear(open, List.of(), caller);
            }
        }
    }

    /** Facts that disagree with at least one other fact over an overlapping validity window. */
    static Set<Fact> competingFacts(List<Fact> facts) {
        Set<Fact> competing = new LinkedHashSet<>();
        for (int i = 0; i < facts.size(); i++) {
            for (int j = i + 1; j < facts.size(); j++) {
                Fact a = facts.get(i);
                Fact b = facts.get(j);
                if (overlaps(a, b) && !canonical(a).equals(canonical(b))) {
                    competing.add(a);
                    competing.add(b);
                }
            }
        }
        return competing;
    }

    /** Half-open intervals [validFrom, validTo); a missing validTo is open-ended. */
    static boolean overlaps(Fact a, Fact b) {
        Instant aTo = a.getValidTo() == null ? Instant.MAX : a.getValidTo();
        Instant bTo = b.getValidTo() == null ? Instant.MAX : b.getValidTo();
        return a.getValidFrom().isBefore(bTo) && b.getValidFrom().isBefore(aTo);
    }

    private static String canonical(Fact fact) {
        return FactValueNormalizer.canonical(fact.getValue(), fact.getUnit());
    }

    private void raiseOrRefresh(UUID transactionId, String key, Set<Fact> competing, FactConflict existing,
                                User caller) {
        Fact sample = competing.iterator().next();
        String title = "Conflicting values for \"" + sample.getLabel() + "\""
                + (sample.getPeriod() != null ? " (" + sample.getPeriod() + ")" : "");
        String description = buildDescription(competing);
        Set<String> values = competing.stream().map(ConflictDetectionService::canonical)
                .collect(Collectors.toCollection(TreeSet::new));

        if (existing != null) {
            boolean wasOpen = existing.getStatus() == ConflictStatus.OPEN;
            existing.reopen(competing);
            Issue issue = existing.getIssue();
            if (issue != null) {
                issue.reopen(description, IssueSeverity.HIGH);
                issue.getRelatedFacts().clear();
                issue.getRelatedFacts().addAll(competing);
            }
            if (!wasOpen) {
                auditService.record(transactionId, caller, AuditAction.CONFLICT_DETECTED, "FactConflict",
                        existing.getId(), "Reopened: " + title, null, String.join(" | ", values), null);
            }
            return;
        }

        Workstream workstream = competing.stream().map(Fact::getWorkstream)
                .min(Comparator.comparing(w -> w.getType().name()))
                .orElse(sample.getWorkstream());
        // conflicts recorded before structured conflicts existed already have an issue under this key
        Issue issue = issueRepository.findFirstByConflictKey(key).orElse(null);
        if (issue == null) {
            issue = new Issue(workstream, title, description, IssueSeverity.HIGH, null, null,
                    caller != null ? caller : sample.getCreatedByUser());
            issue.setConflictKey(key);
        } else {
            issue.reopen(description, IssueSeverity.HIGH);
            issue.getRelatedFacts().clear();
        }
        issue.getRelatedFacts().addAll(competing);
        for (Fact fact : competing) {
            issue.getRelatedEvidence().addAll(fact.getEvidence());
        }
        issueRepository.save(issue);

        FactConflict conflict = new FactConflict(transactionId, key, sample.getFactKey(), sample.getPeriod(), issue);
        conflict.reopen(competing);
        conflictRepository.save(conflict);

        auditService.record(transactionId, caller, AuditAction.CONFLICT_DETECTED, "FactConflict", conflict.getId(),
                title, null, String.join(" | ", values), null);
        log.info("Conflict detected on transaction {}: {} -> {}", transactionId, key, values);
    }

    private void clear(FactConflict conflict, List<Fact> remaining, User caller) {
        conflict.clear();
        Issue issue = conflict.getIssue();
        String note = remaining.isEmpty() ? "No competing facts remain."
                : "No competing values remain: " + remaining.stream().map(ConflictDetectionService::canonical)
                        .distinct().collect(Collectors.joining(", "));
        if (issue != null && issue.getStatus() != IssueStatus.RESOLVED && issue.getStatus() != IssueStatus.CLOSED) {
            issue.autoResolve(note);
        }
        auditService.record(conflict.getTransactionId(), caller, AuditAction.CONFLICT_CLEARED, "FactConflict",
                conflict.getId(), note);
    }

    /** Lists every competing value with the document it came from, so a reviewer can adjudicate. */
    private String buildDescription(Set<Fact> facts) {
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
        sb.append("\n\nResolve the conflict by choosing the value that stands and recording why.");
        String text = sb.toString();
        return text.length() > 1000 ? text.substring(0, 997) + "..." : text;
    }

    /**
     * Facts collide only when they share a semantic key and period. The subject is implicitly the
     * issuer until subject entities exist (plan C1); labels are display text and play no part.
     */
    private static String conflictKey(UUID transactionId, Fact fact) {
        String period = fact.getPeriod() == null ? "" : fact.getPeriod().trim().toLowerCase(Locale.ROOT);
        return transactionId + "::" + fact.getFactKey() + "::" + period;
    }
}
