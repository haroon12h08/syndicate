package com.syndicate.registry;

import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditService;
import com.syndicate.company.Company;
import com.syndicate.company.CompanyService;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.issue.Issue;
import com.syndicate.issue.IssueRepository;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.issue.IssueStatus;
import com.syndicate.provenance.MerkleTree;
import com.syndicate.registry.dto.RegistryVerificationDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionRepository;
import com.syndicate.user.User;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import com.syndicate.workstream.WorkstreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-checks a company against an external registry and raises conflict issues for anything that
 * disagrees (§18, §49).
 *
 * <p>As with internal conflict detection, the registry is never treated as automatically correct:
 * a mismatch produces an issue showing both values so a human decides which source wins.
 */
@Service
public class RegistryVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RegistryVerificationService.class);

    /** Capital is recorded as a fact rather than a company column, so it is matched by label. */
    private static final String AUTHORIZED_CAPITAL_LABEL = "authorized capital";

    public static final String NEVER_RUN = "NEVER_RUN";
    public static final String VERIFIED_MATCH = "VERIFIED_MATCH";
    public static final String MISMATCH_DETECTED = "MISMATCH_DETECTED";
    public static final String NOT_FOUND = "NOT_IN_REGISTRY";

    private final ExternalRegistryService registry;
    private final RegistrySnapshotRepository snapshotRepository;
    private final CompanyService companyService;
    private final TransactionRepository transactionRepository;
    private final WorkstreamRepository workstreamRepository;
    private final FactRepository factRepository;
    private final IssueRepository issueRepository;
    private final AuditService auditService;

    public RegistryVerificationService(ExternalRegistryService registry,
                                        RegistrySnapshotRepository snapshotRepository,
                                        CompanyService companyService,
                                        TransactionRepository transactionRepository,
                                        WorkstreamRepository workstreamRepository,
                                        FactRepository factRepository,
                                        IssueRepository issueRepository,
                                        AuditService auditService) {
        this.registry = registry;
        this.snapshotRepository = snapshotRepository;
        this.companyService = companyService;
        this.transactionRepository = transactionRepository;
        this.workstreamRepository = workstreamRepository;
        this.factRepository = factRepository;
        this.issueRepository = issueRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RegistryVerificationDto verify(UUID companyId, User caller) {
        Company company = companyService.findCompanyForUser(companyId, caller.getId());

        Optional<RegistryRecord> found = registry.lookup(company.getCin(), company.getPan());
        if (found.isEmpty()) {
            return new RegistryVerificationDto(companyId, NOT_FOUND, registry.source(),
                    company.getCin() != null ? company.getCin() : company.getPan(),
                    false, null, null, 0, 0, List.of(), List.of(),
                    "The registry has no record for this CIN/PAN. Check the identifier on file.");
        }

        RegistryRecord record = found.get();
        String payloadHash = MerkleTree.sha256Hex(record.canonical());
        RegistrySnapshot snapshot =
                snapshotRepository.save(new RegistrySnapshot(company, record, payloadHash, caller));

        Transaction transaction = latestTransactionFor(companyId);
        List<RegistryVerificationDto.FieldComparisonDto> comparisons = new ArrayList<>();
        List<UUID> raisedIssues = new ArrayList<>();

        comparisons.add(compareText(company, transaction, caller, "Legal name",
                company.getLegalName(), record.legalName(), "Company record", raisedIssues));

        comparisons.add(compareText(company, transaction, caller, "Incorporation date",
                company.getIncorporationDate() != null ? company.getIncorporationDate().toString() : null,
                record.incorporationDate() != null ? record.incorporationDate().toString() : null,
                "Company record", raisedIssues));

        comparisons.add(compareAuthorizedCapital(company, transaction, caller, record, raisedIssues));

        int mismatches = (int) comparisons.stream().filter(c -> !c.match()).count();

        if (transaction != null) {
            auditService.record(transaction.getId(), caller, AuditAction.CONFLICT_DETECTED,
                    "RegistrySnapshot", snapshot.getId(),
                    "Registry verification against " + registry.source() + ": "
                            + mismatches + " mismatch(es) of " + comparisons.size() + " fields checked",
                    null, payloadHash, null);
        }

        String message = mismatches == 0
                ? "Every checked field matches the registry."
                : mismatches + " field(s) disagree with the registry. Issues raised for review.";
        if (transaction == null && mismatches > 0) {
            message += " No transaction exists for this company yet, so the findings could not be"
                    + " filed as issues.";
        }

        return new RegistryVerificationDto(companyId,
                mismatches == 0 ? VERIFIED_MATCH : MISMATCH_DETECTED,
                registry.source(), record.lookupKey(), true,
                payloadHash, snapshot.getFetchedAt(), comparisons.size(), mismatches,
                comparisons, raisedIssues, message);
    }

    /**
     * Re-derives the comparison from the last stored snapshot without calling the registry or
     * touching issues, so the page can show current standing on load. Internal values are read
     * live, so correcting a fact flips the card to matched without another registry call.
     */
    @Transactional(readOnly = true)
    public RegistryVerificationDto status(UUID companyId, UUID callerId) {
        Company company = companyService.findCompanyForUser(companyId, callerId);
        RegistrySnapshot snapshot = snapshotRepository
                .findByCompanyIdOrderByFetchedAtDesc(companyId).stream().findFirst().orElse(null);

        if (snapshot == null) {
            return new RegistryVerificationDto(companyId, NEVER_RUN, registry.source(),
                    company.getCin() != null ? company.getCin() : company.getPan(),
                    false, null, null, 0, 0, List.of(), List.of(),
                    "This company has not been checked against the registry yet.");
        }

        Transaction transaction = latestTransactionFor(companyId);
        List<RegistryVerificationDto.FieldComparisonDto> comparisons = List.of(
                readOnlyComparison(company, "Legal name", company.getLegalName(),
                        snapshot.getLegalName(), "Company record"),
                readOnlyComparison(company, "Incorporation date",
                        company.getIncorporationDate() != null ? company.getIncorporationDate().toString() : null,
                        snapshot.getIncorporationDate() != null ? snapshot.getIncorporationDate().toString() : null,
                        "Company record"),
                authorizedCapitalComparison(company, transaction, snapshot.getAuthorizedCapital()));

        int mismatches = (int) comparisons.stream().filter(c -> !c.match()).count();
        return new RegistryVerificationDto(companyId,
                mismatches == 0 ? VERIFIED_MATCH : MISMATCH_DETECTED,
                snapshot.getSource(), snapshot.getLookupKey(), true, snapshot.getPayloadSha256(),
                snapshot.getFetchedAt(), comparisons.size(), mismatches, comparisons, List.of(),
                mismatches == 0 ? "Every checked field matches the registry."
                        : mismatches + " field(s) disagree with the registry.");
    }

    private RegistryVerificationDto.FieldComparisonDto readOnlyComparison(
            Company company, String field, String internalValue, String registryValue, String internalSource) {
        boolean match = internalValue != null && registryValue != null
                && normalise(internalValue).equals(normalise(registryValue));
        Issue issue = issueRepository.findFirstByConflictKey(conflictKey(company, field)).orElse(null);
        boolean openIssue = issue != null && issue.getStatus() != IssueStatus.RESOLVED
                && issue.getStatus() != IssueStatus.CLOSED;
        return new RegistryVerificationDto.FieldComparisonDto(field, internalValue, registryValue,
                match, internalSource, openIssue ? issue.getId() : null,
                openIssue ? issue.getWorkstream().getId() : null);
    }

    private RegistryVerificationDto.FieldComparisonDto authorizedCapitalComparison(
            Company company, Transaction transaction, BigDecimal registryCapital) {
        Fact fact = findAuthorizedCapitalFact(transaction);
        String internalValue = fact != null ? fact.getValue() : null;
        String registryValue = registryCapital != null
                ? registryCapital.stripTrailingZeros().toPlainString() : null;
        boolean match = capitalMatches(internalValue, registryCapital);

        Issue issue = issueRepository.findFirstByConflictKey(conflictKey(company, "Authorized capital"))
                .orElse(null);
        boolean openIssue = issue != null && issue.getStatus() != IssueStatus.RESOLVED
                && issue.getStatus() != IssueStatus.CLOSED;

        return new RegistryVerificationDto.FieldComparisonDto("Authorized capital", internalValue,
                registryValue, match,
                fact != null ? "Fact \"" + fact.getLabel() + "\"" : "Not recorded",
                openIssue ? issue.getId() : null, openIssue ? issue.getWorkstream().getId() : null);
    }

    private Fact findAuthorizedCapitalFact(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        return factRepository.findByWorkstreamTransactionId(transaction.getId()).stream()
                .filter(f -> f.getStatus() != FactStatus.SUPERSEDED)
                .filter(f -> f.getLabel().trim().toLowerCase(Locale.ROOT).contains(AUTHORIZED_CAPITAL_LABEL))
                .findFirst()
                .orElse(null);
    }

    private boolean capitalMatches(String internalValue, BigDecimal registryCapital) {
        if (internalValue == null || registryCapital == null) {
            return false;
        }
        try {
            return new BigDecimal(internalValue.replaceAll("[,\\s₹]", "")).compareTo(registryCapital) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String conflictKey(Company company, String field) {
        return "registry::" + company.getId() + "::" + field.toLowerCase(Locale.ROOT);
    }

    private RegistryVerificationDto.FieldComparisonDto compareText(
            Company company, Transaction transaction, User caller, String field,
            String internalValue, String registryValue, String internalSource, List<UUID> raised) {

        boolean match = normalise(internalValue).equals(normalise(registryValue))
                && internalValue != null && registryValue != null;
        Issue issue = syncIssue(company, transaction, caller, field, internalValue, registryValue,
                internalSource, match, raised);
        return new RegistryVerificationDto.FieldComparisonDto(field, internalValue, registryValue,
                match, internalSource, issue != null ? issue.getId() : null,
                issue != null ? issue.getWorkstream().getId() : null);
    }

    /**
     * Authorized capital lives as a verified fact, so the comparison is numeric and names the
     * fact that disagrees rather than a company column.
     */
    private RegistryVerificationDto.FieldComparisonDto compareAuthorizedCapital(
            Company company, Transaction transaction, User caller, RegistryRecord record, List<UUID> raised) {

        Fact fact = transaction == null ? null : factRepository
                .findByWorkstreamTransactionId(transaction.getId()).stream()
                .filter(f -> f.getStatus() != FactStatus.SUPERSEDED)
                .filter(f -> f.getLabel().trim().toLowerCase(Locale.ROOT).contains(AUTHORIZED_CAPITAL_LABEL))
                .findFirst()
                .orElse(null);

        String internalValue = fact != null ? fact.getValue() : null;
        String registryValue = record.authorizedCapital() != null
                ? record.authorizedCapital().stripTrailingZeros().toPlainString() : null;

        boolean match = false;
        if (internalValue != null && record.authorizedCapital() != null) {
            try {
                BigDecimal internal = new BigDecimal(internalValue.replaceAll("[,\\s₹]", ""));
                match = internal.compareTo(record.authorizedCapital()) == 0;
            } catch (NumberFormatException e) {
                match = false;
            }
        }

        Issue issue = syncIssue(company, transaction, caller, "Authorized capital",
                internalValue, registryValue,
                fact != null ? "Fact \"" + fact.getLabel() + "\"" : "Not recorded", match, raised);

        return new RegistryVerificationDto.FieldComparisonDto("Authorized capital",
                internalValue, registryValue, match,
                fact != null ? "Fact \"" + fact.getLabel() + "\"" : "Not recorded",
                issue != null ? issue.getId() : null,
                issue != null ? issue.getWorkstream().getId() : null);
    }

    /** One issue per company field, reopened on mismatch and auto-resolved once it agrees. */
    private Issue syncIssue(Company company, Transaction transaction, User caller, String field,
                            String internalValue, String registryValue, String internalSource,
                            boolean match, List<UUID> raised) {

        String conflictKey = conflictKey(company, field);
        Issue existing = issueRepository.findFirstByConflictKey(conflictKey).orElse(null);

        if (match) {
            if (existing != null && existing.getStatus() != IssueStatus.RESOLVED
                    && existing.getStatus() != IssueStatus.CLOSED) {
                existing.autoResolve("Now matches " + registry.source() + ": " + registryValue);
            }
            return null;
        }

        String description = "Syndicate and " + registry.source() + " disagree. No value has been"
                + " selected automatically.\n\n• Syndicate: " + display(internalValue)
                + "  (" + internalSource + ")\n• " + registry.source() + ": " + display(registryValue)
                + "  (external registry snapshot)\n\nConfirm which source is correct and correct the"
                + " other, or record why they legitimately differ.";

        if (existing != null) {
            existing.reopen(description, IssueSeverity.HIGH);
            raised.add(existing.getId());
            return existing;
        }

        if (transaction == null) {
            log.info("Registry mismatch on {} for company {} but no transaction exists to file it against",
                    field, company.getId());
            return null;
        }

        Workstream workstream = resolveWorkstream(transaction);
        Issue issue = new Issue(workstream,
                "Registry mismatch: " + field + " (" + registry.source() + ")",
                description, IssueSeverity.HIGH, null, null, caller);
        issue.setConflictKey(conflictKey);
        Issue saved = issueRepository.save(issue);
        raised.add(saved.getId());
        return saved;
    }

    private Workstream resolveWorkstream(Transaction transaction) {
        return workstreamRepository.findByTransactionId(transaction.getId()).stream()
                .filter(w -> w.getType() == WorkstreamType.CORPORATE_SECRETARIAL)
                .findFirst()
                .orElseGet(() -> workstreamRepository.save(new Workstream(transaction,
                        WorkstreamType.CORPORATE_SECRETARIAL,
                        "Opened automatically for registry verification findings")));
    }

    private Transaction latestTransactionFor(UUID companyId) {
        return transactionRepository.findByCompanyId(companyId).stream()
                .max(Comparator.comparing(Transaction::getCreatedAt))
                .orElse(null);
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "(not recorded)" : value;
    }

    private String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
