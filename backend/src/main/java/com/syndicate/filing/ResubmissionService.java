package com.syndicate.filing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.audit.AuditAction;
import com.syndicate.audit.AuditEvent;
import com.syndicate.audit.AuditEventRepository;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.drhp.dto.CompiledSection;
import com.syndicate.drhp.dto.CompiledSegment;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.filing.dto.FilingDiffDto;
import com.syndicate.observation.RegulatoryObservation;
import com.syndicate.observation.RegulatoryObservationRepository;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spec §18, §37 & Build Plan Ticket F4: Resubmission tracking and version diffing.
 *
 * <p>Produces side-by-side comparison between filed DRHP packages and resubmission versions,
 * detailing changed disclosures, superseded facts, change justifications, and addressed observations.
 */
@Service
public class ResubmissionService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final DrhpDocumentRepository documentRepository;
    private final FilingPackageRepository packageRepository;
    private final AuditEventRepository auditEventRepository;
    private final RegulatoryObservationRepository observationRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    public ResubmissionService(DrhpDocumentRepository documentRepository,
                               FilingPackageRepository packageRepository,
                               AuditEventRepository auditEventRepository,
                               RegulatoryObservationRepository observationRepository,
                               TransactionService transactionService,
                               ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.packageRepository = packageRepository;
        this.auditEventRepository = auditEventRepository;
        this.observationRepository = observationRepository;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FilingDiffDto computeDiff(UUID transactionId, UUID baseDocumentId, UUID targetDocumentId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);

        DrhpDocument baseDoc = findDocument(baseDocumentId);
        DrhpDocument targetDoc = findDocument(targetDocumentId);

        if (!baseDoc.getTransaction().getId().equals(transactionId) || !targetDoc.getTransaction().getId().equals(transactionId)) {
            throw new IllegalArgumentException("Documents do not belong to the specified transaction");
        }

        return computeDiffInternal(baseDoc, targetDoc);
    }

    @Transactional(readOnly = true)
    public FilingDiffDto computeDiffAgainstPrevious(UUID targetDocumentId, UUID callerId) {
        DrhpDocument targetDoc = findDocument(targetDocumentId);
        UUID transactionId = targetDoc.getTransaction().getId();
        transactionService.requireMembership(transactionId, callerId);

        List<DrhpDocument> versions = documentRepository.findByTransactionIdOrderByVersionDesc(transactionId);
        DrhpDocument previous = versions.stream()
                .filter(d -> d.getVersion() < targetDoc.getVersion())
                .findFirst()
                .orElse(null);

        if (previous == null) {
            // Compare against itself (clean diff with zero changes)
            return computeDiffInternal(targetDoc, targetDoc);
        }

        return computeDiffInternal(previous, targetDoc);
    }

    public FilingDiffDto computeDiffForDocuments(DrhpDocument baseDoc, DrhpDocument targetDoc) {
        return computeDiffInternal(baseDoc, targetDoc);
    }

    private FilingDiffDto computeDiffInternal(DrhpDocument baseDoc, DrhpDocument targetDoc) {
        UUID transactionId = targetDoc.getTransaction().getId();

        // Packages
        Optional<FilingPackage> basePkg = packageRepository.findByDocumentId(baseDoc.getId());
        Optional<FilingPackage> targetPkg = packageRepository.findByDocumentId(targetDoc.getId());

        // 1. Facts Diff
        Map<UUID, Fact> baseFactsByLineage = new HashMap<>();
        Map<String, Fact> baseFactsByKeyPeriod = new HashMap<>();
        for (Fact f : baseDoc.getCitedFacts()) {
            if (f.getLineageId() != null) {
                baseFactsByLineage.put(f.getLineageId(), f);
            }
            baseFactsByKeyPeriod.put(keyPeriod(f.getFactKey(), f.getPeriod()), f);
        }

        Map<UUID, Fact> targetFactsByLineage = new HashMap<>();
        Map<String, Fact> targetFactsByKeyPeriod = new HashMap<>();
        for (Fact f : targetDoc.getCitedFacts()) {
            if (f.getLineageId() != null) {
                targetFactsByLineage.put(f.getLineageId(), f);
            }
            targetFactsByKeyPeriod.put(keyPeriod(f.getFactKey(), f.getPeriod()), f);
        }

        List<FilingDiffDto.FactDiffDto> factDiffs = new ArrayList<>();
        int factsSuperseded = 0;
        int factsAdded = 0;
        int factsRemoved = 0;
        int factsUnchanged = 0;

        Set<String> processedKeys = new HashSet<>();

        // Facts in target
        for (Fact targetFact : targetDoc.getCitedFacts()) {
            String kp = keyPeriod(targetFact.getFactKey(), targetFact.getPeriod());
            processedKeys.add(kp);

            Fact baseFact = baseFactsByLineage.get(targetFact.getLineageId());
            if (baseFact == null) {
                baseFact = baseFactsByKeyPeriod.get(kp);
            }

            if (baseFact == null) {
                factsAdded++;
                factDiffs.add(buildFactDiff("ADDED", null, targetFact));
            } else if (!Objects.equals(baseFact.getValue(), targetFact.getValue())
                    || baseFact.getVersion() != targetFact.getVersion()) {
                factsSuperseded++;
                factDiffs.add(buildFactDiff("SUPERSEDED_VALUE_CHANGED", baseFact, targetFact));
            } else {
                factsUnchanged++;
                factDiffs.add(buildFactDiff("UNCHANGED", baseFact, targetFact));
            }
        }

        // Facts in base that are absent in target
        for (Fact baseFact : baseDoc.getCitedFacts()) {
            String kp = keyPeriod(baseFact.getFactKey(), baseFact.getPeriod());
            if (!processedKeys.contains(kp) && !targetFactsByLineage.containsKey(baseFact.getLineageId())) {
                factsRemoved++;
                factDiffs.add(buildFactDiff("REMOVED", baseFact, null));
            }
        }

        // 2. Disclosures Diff
        List<CompiledSection> baseSections = readSections(baseDoc.getCompiledBody());
        List<CompiledSection> targetSections = readSections(targetDoc.getCompiledBody());

        Map<String, CompiledSection> baseSecMap = new LinkedHashMap<>();
        for (CompiledSection s : baseSections) {
            baseSecMap.put(s.sectionCode(), s);
        }

        Map<String, CompiledSection> targetSecMap = new LinkedHashMap<>();
        for (CompiledSection s : targetSections) {
            targetSecMap.put(s.sectionCode(), s);
        }

        List<FilingDiffDto.DisclosureDiffDto> disclosureDiffs = new ArrayList<>();
        int discModified = 0;
        int discAdded = 0;
        int discRemoved = 0;
        int discUnchanged = 0;

        Set<String> processedSections = new HashSet<>();

        for (CompiledSection targetSec : targetSections) {
            processedSections.add(targetSec.sectionCode());
            CompiledSection baseSec = baseSecMap.get(targetSec.sectionCode());

            String targetProse = toProse(targetSec);
            if (baseSec == null) {
                discAdded++;
                disclosureDiffs.add(new FilingDiffDto.DisclosureDiffDto(
                        targetSec.sectionCode(), targetSec.title(), "ADDED", null, targetProse, extractFactKeys(targetSec)
                ));
            } else {
                String baseProse = toProse(baseSec);
                if (!baseProse.equals(targetProse)) {
                    discModified++;
                    disclosureDiffs.add(new FilingDiffDto.DisclosureDiffDto(
                            targetSec.sectionCode(), targetSec.title(), "MODIFIED", baseProse, targetProse, extractFactKeys(targetSec)
                    ));
                } else {
                    discUnchanged++;
                    disclosureDiffs.add(new FilingDiffDto.DisclosureDiffDto(
                            targetSec.sectionCode(), targetSec.title(), "UNCHANGED", baseProse, targetProse, extractFactKeys(targetSec)
                    ));
                }
            }
        }

        for (CompiledSection baseSec : baseSections) {
            if (!processedSections.contains(baseSec.sectionCode())) {
                discRemoved++;
                disclosureDiffs.add(new FilingDiffDto.DisclosureDiffDto(
                        baseSec.sectionCode(), baseSec.title(), "REMOVED", toProse(baseSec), null, extractFactKeys(baseSec)
                ));
            }
        }

        // 3. Addressed Regulatory Observations
        List<RegulatoryObservation> observations = observationRepository.findByTransactionIdOrderByReceivedDateDesc(transactionId);
        List<FilingDiffDto.ObservationDiffDto> obsDtos = observations.stream()
                .map(o -> new FilingDiffDto.ObservationDiffDto(
                        o.getId(),
                        o.getAuthority(),
                        o.getReference(),
                        o.getSectionCode(),
                        o.getObservation(),
                        o.getStatus().name(),
                        o.getResponse(),
                        o.getApprovedAt()
                ))
                .toList();

        FilingDiffDto.DiffStatsDto stats = new FilingDiffDto.DiffStatsDto(
                factsSuperseded,
                factsAdded,
                factsRemoved,
                factsUnchanged,
                discModified,
                discAdded,
                discRemoved,
                discUnchanged,
                (int) observations.stream().filter(o -> o.getApprovedAt() != null).count()
        );

        return new FilingDiffDto(
                transactionId,
                toDocRef(baseDoc),
                toDocRef(targetDoc),
                basePkg.map(this::toPkgRef).orElse(null),
                targetPkg.map(this::toPkgRef).orElse(null),
                stats,
                factDiffs,
                disclosureDiffs,
                obsDtos
        );
    }

    @Transactional(readOnly = true)
    public String getStatementOfChangesText(UUID documentId, UUID callerId) {
        DrhpDocument doc = findDocument(documentId);
        transactionService.requireMembership(doc.getTransaction().getId(), callerId);
        FilingDiffDto diff = computeDiffAgainstPrevious(documentId, callerId);
        String txName = doc.getTransaction().getName();
        String compName = doc.getTransaction().getCompany() != null
                ? doc.getTransaction().getCompany().getLegalName() : null;
        return generateStatementOfChangesText(diff, txName, compName);
    }

    public String generateStatementOfChangesText(FilingDiffDto diff, String transactionName, String companyName) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("STATEMENT OF CHANGES & RESUBMISSION EXPLANATION\n");
        sb.append("================================================================================\n");
        sb.append("Transaction: ").append(transactionName != null ? transactionName : "Transaction")
                .append(" (").append(companyName != null ? companyName : "Company").append(")\n");
        sb.append("Base Filing: DRHP v").append(diff.baseDocument().version())
                .append(" (Compiled ").append(DATE_FMT.format(diff.baseDocument().compiledAt())).append(")\n");
        sb.append("Resubmission: DRHP v").append(diff.targetDocument().version())
                .append(" (Compiled ").append(DATE_FMT.format(diff.targetDocument().compiledAt())).append(")\n");
        if (diff.targetDocument().merkleRoot() != null) {
            sb.append("Target Merkle Root: ").append(diff.targetDocument().merkleRoot()).append("\n");
        }
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("1. SUMMARY OF MATERIAL CHANGES\n");
        sb.append("   - Facts Superseded / Corrected: ").append(diff.stats().factsSuperseded()).append("\n");
        sb.append("   - Facts Added:                  ").append(diff.stats().factsAdded()).append("\n");
        sb.append("   - Disclosures Modified:         ").append(diff.stats().disclosuresModified()).append("\n");
        sb.append("   - Observations Addressed:       ").append(diff.stats().observationsAddressed()).append("\n");
        sb.append("\n");

        sb.append("2. SCHEDULE OF FACT CORRECTIONS & BASIS\n");
        List<FilingDiffDto.FactDiffDto> changedFacts = diff.facts().stream()
                .filter(f -> !"UNCHANGED".equals(f.changeType()))
                .toList();

        if (changedFacts.isEmpty()) {
            sb.append("   No underlying facts were modified between these versions.\n");
        } else {
            int num = 1;
            for (FilingDiffDto.FactDiffDto f : changedFacts) {
                sb.append("   [").append(num++).append("] ").append(f.label()).append(" (").append(f.factKey()).append(")\n");
                if (f.period() != null) {
                    sb.append("       Period:            ").append(f.period()).append("\n");
                }
                sb.append("       Status:            ").append(f.changeType()).append("\n");
                sb.append("       Previous Value:    ").append(f.baseValue() != null ? f.baseValue() : "None").append("\n");
                sb.append("       Corrected Value:   ").append(f.targetValue() != null ? f.targetValue() : "None")
                        .append(f.unit() != null ? " " + f.unit() : "").append("\n");
                if (f.changeReason() != null) {
                    sb.append("       Reason for Change: ").append(f.changeReason()).append("\n");
                }
                if (f.correctedBy() != null) {
                    sb.append("       Corrected By:      ").append(f.correctedBy())
                            .append(f.correctedAt() != null ? " on " + DATE_FMT.format(f.correctedAt()) : "").append("\n");
                }
                if (f.verifiedBy() != null) {
                    sb.append("       Verified By:       ").append(f.verifiedBy())
                            .append(f.verifiedAt() != null ? " on " + DATE_FMT.format(f.verifiedAt()) : "").append("\n");
                }
                if (f.targetEvidenceFiles() != null && !f.targetEvidenceFiles().isEmpty()) {
                    sb.append("       Evidence Relied:   ").append(String.join(", ", f.targetEvidenceFiles())).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("3. DISCLOSURE MODIFICATIONS\n");
        List<FilingDiffDto.DisclosureDiffDto> changedDiscs = diff.disclosures().stream()
                .filter(d -> !"UNCHANGED".equals(d.changeType()))
                .toList();

        if (changedDiscs.isEmpty()) {
            sb.append("   No disclosures were modified between these versions.\n");
        } else {
            for (FilingDiffDto.DisclosureDiffDto d : changedDiscs) {
                sb.append("   Section: ").append(d.sectionCode()).append(" — ").append(d.title()).append("\n");
                sb.append("   Change Type: ").append(d.changeType()).append("\n");
                if (d.affectedFactKeys() != null && !d.affectedFactKeys().isEmpty()) {
                    sb.append("   Affected Facts: ").append(String.join(", ", d.affectedFactKeys())).append("\n");
                }
                if (d.baseProse() != null) {
                    sb.append("   Previous Text:\n   \"").append(d.baseProse().trim()).append("\"\n");
                }
                if (d.targetProse() != null) {
                    sb.append("   Updated Text:\n   \"").append(d.targetProse().trim()).append("\"\n");
                }
                sb.append("\n");
            }
        }

        sb.append("4. REGULATORY OBSERVATIONS\n");
        if (diff.addressedObservations().isEmpty()) {
            sb.append("   No regulatory observations recorded for this transaction.\n");
        } else {
            for (FilingDiffDto.ObservationDiffDto o : diff.addressedObservations()) {
                sb.append("   Query Ref: ").append(o.reference() != null ? o.reference() : o.id().toString().substring(0, 8))
                        .append(" (").append(o.authority()).append(")\n");
                sb.append("   Observation: ").append(o.observation()).append("\n");
                sb.append("   Status:      ").append(o.status()).append("\n");
                if (o.responseDraft() != null) {
                    sb.append("   Response:    ").append(o.responseDraft()).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("================================================================================\n");
        sb.append("End of Statement of Changes. Produced by Syndicate Diligence & Disclosure Control.\n");
        return sb.toString();
    }

    public String generateStatementOfChangesJson(FilingDiffDto diff) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(diff);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize statement of changes JSON: " + e.getMessage(), e);
        }
    }

    private FilingDiffDto.FactDiffDto buildFactDiff(String changeType, Fact baseFact, Fact targetFact) {
        Fact primary = targetFact != null ? targetFact : baseFact;
        UUID factId = targetFact != null ? targetFact.getId() : baseFact.getId();

        String reason = null;
        String correctedBy = null;
        Instant correctedAt = null;

        // Try to fetch superseding reason and actor from audit trail
        List<AuditEvent> events = auditEventRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Fact", factId);
        for (AuditEvent ev : events) {
            if (ev.getAction() == AuditAction.FACT_SUPERSEDED) {
                reason = ev.getReason();
                correctedBy = ev.getActorUser() != null ? ev.getActorUser().getFullName() : null;
                correctedAt = ev.getOccurredAt();
                break;
            }
        }

        return new FilingDiffDto.FactDiffDto(
                primary.getLineageId(),
                primary.getFactKey(),
                primary.getLabel(),
                primary.getPeriod(),
                primary.getUnit(),
                changeType,
                baseFact != null ? baseFact.getValue() : null,
                targetFact != null ? targetFact.getValue() : null,
                baseFact != null ? baseFact.getVersion() : null,
                targetFact != null ? targetFact.getVersion() : null,
                reason,
                correctedBy,
                correctedAt,
                primary.getVerifiedByUser() != null ? primary.getVerifiedByUser().getFullName() : null,
                primary.getVerifiedAt(),
                baseFact != null && baseFact.getEvidence() != null ? baseFact.getEvidence().stream().map(Evidence::getFileName).toList() : List.of(),
                targetFact != null && targetFact.getEvidence() != null ? targetFact.getEvidence().stream().map(Evidence::getFileName).toList() : List.of()
        );
    }

    private String keyPeriod(String key, String period) {
        return (key != null ? key.toLowerCase() : "") + "@" + (period != null ? period.toLowerCase() : "");
    }

    private String toProse(CompiledSection section) {
        if (section.segments() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (CompiledSegment seg : section.segments()) {
            if ("FACT".equalsIgnoreCase(seg.type())) {
                sb.append(seg.value() != null ? seg.value() : seg.label());
            } else if ("UNRESOLVED".equalsIgnoreCase(seg.type())) {
                sb.append("[UNRESOLVED: ").append(seg.label()).append("]");
            } else {
                sb.append(seg.text() != null ? seg.text() : "");
            }
        }
        return sb.toString();
    }

    private List<String> extractFactKeys(CompiledSection section) {
        if (section.segments() == null) return List.of();
        List<String> keys = new ArrayList<>();
        for (CompiledSegment s : section.segments()) {
            if ("FACT".equalsIgnoreCase(s.type()) && s.label() != null) {
                keys.add(s.label());
            }
        }
        return keys;
    }

    private FilingDiffDto.DocumentRefDto toDocRef(DrhpDocument doc) {
        return new FilingDiffDto.DocumentRefDto(
                doc.getId(),
                doc.getVersion(),
                doc.getCompileMode() != null ? doc.getCompileMode().name() : null,
                doc.getMerkleRoot(),
                doc.getCompiledAt(),
                doc.getCompiledByUser() != null ? doc.getCompiledByUser().getFullName() : "System"
        );
    }

    private FilingDiffDto.PackageRefDto toPkgRef(FilingPackage pkg) {
        return new FilingDiffDto.PackageRefDto(
                pkg.getId(),
                pkg.getFileName(),
                pkg.getSha256(),
                pkg.getSizeBytes(),
                pkg.getBuiltAt()
        );
    }

    private DrhpDocument findDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("DRHP Document not found: " + documentId));
    }

    private List<CompiledSection> readSections(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CompiledSection>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse compiled sections: " + e.getMessage(), e);
        }
    }
}
