package com.syndicate.drhp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.common.BadRequestException;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.drhp.dto.CompileResultDto;
import com.syndicate.drhp.dto.CompiledSection;
import com.syndicate.drhp.dto.DisclosureDto;
import com.syndicate.drhp.dto.DrhpDocumentDto;
import com.syndicate.drhp.dto.LintFinding;
import com.syndicate.drhp.dto.SaveDisclosureRequest;
import com.syndicate.fact.Fact;
import com.syndicate.provenance.ProvenanceService;
import com.syndicate.fact.FactRepository;
import com.syndicate.regulatory.RuleEvaluation;
import com.syndicate.regulatory.RuleEvaluationRepository;
import com.syndicate.regulatory.RuleEvaluationStatus;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DrhpService {

    private final DisclosureRepository disclosureRepository;
    private final DrhpDocumentRepository drhpRepository;
    private final FactRepository factRepository;
    private final RuleEvaluationRepository ruleEvaluationRepository;
    private final TransactionService transactionService;
    private final DrhpCompiler compiler;
    private final ProvenanceService provenanceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DrhpService(DisclosureRepository disclosureRepository,
                        DrhpDocumentRepository drhpRepository,
                        FactRepository factRepository,
                        RuleEvaluationRepository ruleEvaluationRepository,
                        TransactionService transactionService,
                        DrhpCompiler compiler,
                        ProvenanceService provenanceService) {
        this.disclosureRepository = disclosureRepository;
        this.drhpRepository = drhpRepository;
        this.factRepository = factRepository;
        this.ruleEvaluationRepository = ruleEvaluationRepository;
        this.transactionService = transactionService;
        this.compiler = compiler;
        this.provenanceService = provenanceService;
    }

    @Transactional(readOnly = true)
    public List<DisclosureDto> listDisclosures(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return disclosureRepository.findByTransactionIdOrderByOrderIndexAscCreatedAtAsc(transactionId).stream()
                .map(DisclosureDto::from)
                .toList();
    }

    @Transactional
    public DisclosureDto createDisclosure(UUID transactionId, SaveDisclosureRequest request, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);
        boolean duplicate = disclosureRepository
                .findByTransactionIdOrderByOrderIndexAscCreatedAtAsc(transactionId).stream()
                .anyMatch(d -> d.getSectionCode().equalsIgnoreCase(request.sectionCode()));
        if (duplicate) {
            throw new BadRequestException("A disclosure with section code "
                    + request.sectionCode() + " already exists");
        }
        Disclosure disclosure = new Disclosure(transaction, request.sectionCode(), request.title(),
                request.bodyTemplate(), request.orderIndex() != null ? request.orderIndex() : 0, caller);
        if (request.status() != null) {
            disclosure.edit(request.title(), request.bodyTemplate(), request.status(),
                    disclosure.getOrderIndex());
        }
        return DisclosureDto.from(disclosureRepository.save(disclosure));
    }

    @Transactional
    public DisclosureDto updateDisclosure(UUID disclosureId, SaveDisclosureRequest request, User caller) {
        Disclosure disclosure = findDisclosure(disclosureId);
        transactionService.requireMembership(disclosure.getTransaction().getId(), caller.getId());
        disclosure.edit(request.title(), request.bodyTemplate(), request.status(),
                request.orderIndex() != null ? request.orderIndex() : disclosure.getOrderIndex());
        return DisclosureDto.from(disclosure);
    }

    /** Records a dependency edge so later fact changes can find this disclosure. */
    @Transactional
    public DisclosureDto linkFact(UUID disclosureId, UUID factId, User caller) {
        Disclosure disclosure = findDisclosure(disclosureId);
        transactionService.requireMembership(disclosure.getTransaction().getId(), caller.getId());
        Fact fact = factRepository.findById(factId)
                .orElseThrow(() -> new ResourceNotFoundException("Fact not found: " + factId));
        if (!fact.getWorkstream().getTransaction().getId().equals(disclosure.getTransaction().getId())) {
            throw new BadRequestException("That fact belongs to a different transaction");
        }
        disclosure.getSourceFacts().add(fact);
        return DisclosureDto.from(disclosure);
    }

    @Transactional
    public DisclosureDto unlinkFact(UUID disclosureId, UUID factId, User caller) {
        Disclosure disclosure = findDisclosure(disclosureId);
        transactionService.requireMembership(disclosure.getTransaction().getId(), caller.getId());
        disclosure.getSourceFacts().removeIf(f -> f.getId().equals(factId));
        return DisclosureDto.from(disclosure);
    }

    /**
     * Compiles the DRHP, but only if every gate passes: no failing or unevidenced regulatory rule,
     * no stale disclosure, and every placeholder resolving to exactly one human-verified fact.
     * A refused compile still returns the preview so the team can see what is missing.
     */
    @Transactional
    public CompileResultDto compile(UUID transactionId, User caller, CompileMode mode) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);

        List<Disclosure> disclosures =
                disclosureRepository.findByTransactionIdOrderByOrderIndexAscCreatedAtAsc(transactionId);
        List<Fact> facts = factRepository.findByWorkstreamTransactionId(transactionId);

        DrhpCompiler.CompileResult result = compiler.compile(disclosures, facts);

        List<LintFinding> findings = new ArrayList<>(result.findings());
        findings.addAll(0, readinessFindings(transactionId));

        if (!findings.isEmpty()) {
            return new CompileResultDto(false, null, findings, result.sections());
        }

        int nextVersion = drhpRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId)
                .map(d -> d.getVersion() + 1)
                .orElse(1);

        DrhpDocument document = new DrhpDocument(transaction, nextVersion, writeSections(result.sections()),
                "Compiled cleanly: " + result.sections().size() + " section(s), "
                        + result.citedFacts().size() + " verified fact(s) cited.", caller, mode);
        document.getCitedFacts().addAll(result.citedFacts());
        drhpRepository.save(document);

        // A final filing carries a cryptographic proof of the exact state it was built from.
        if (mode == CompileMode.FINAL_FILING) {
            document.attachMerkleRoot(provenanceService.createManifest(document, caller).getMerkleRoot());
        }

        return new CompileResultDto(true, toDto(document), List.of(), result.sections());
    }

    /** The readiness engine is a compile gate: a failing SEBI rule blocks the filing document. */
    private List<LintFinding> readinessFindings(UUID transactionId) {
        List<LintFinding> findings = new ArrayList<>();
        List<RuleEvaluation> evaluations = ruleEvaluationRepository.findByTransactionId(transactionId);
        if (evaluations.isEmpty()) {
            findings.add(new LintFinding("READINESS_NOT_EVALUATED", null, null,
                    "Regulatory readiness has never been evaluated for this transaction."));
            return findings;
        }
        for (RuleEvaluation evaluation : evaluations) {
            if (evaluation.getStatus() == RuleEvaluationStatus.FAILED
                    || evaluation.getStatus() == RuleEvaluationStatus.MISSING_EVIDENCE) {
                findings.add(new LintFinding("READINESS_" + evaluation.getStatus().name(), null, null,
                        evaluation.getRule().getCode() + ": " + evaluation.getDetail()));
            }
        }
        return findings;
    }

    @Transactional(readOnly = true)
    public DrhpDocumentDto latest(UUID transactionId, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        return drhpRepository.findFirstByTransactionIdOrderByVersionDesc(transactionId)
                .map(this::toDto)
                .orElse(null);
    }

    private DrhpDocumentDto toDto(DrhpDocument document) {
        return new DrhpDocumentDto(
                document.getId(),
                document.getTransaction().getId(),
                document.getVersion(),
                document.getStatus(),
                readSections(document.getCompiledBody()),
                document.getLintSummary(),
                document.getInvalidatedReason(),
                document.getCompiledAt(),
                document.getCompiledByUser() != null ? document.getCompiledByUser().getFullName() : null,
                document.getCitedFacts().stream().map(f -> f.getId()).toList(),
                document.getCompileMode() != null ? document.getCompileMode().name() : null,
                document.getMerkleRoot());
    }

    private String writeSections(List<CompiledSection> sections) {
        try {
            return objectMapper.writeValueAsString(sections);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise compiled DRHP: " + e.getMessage(), e);
        }
    }

    private List<CompiledSection> readSections(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<CompiledSection>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Could not read compiled DRHP: " + e.getMessage(), e);
        }
    }

    private Disclosure findDisclosure(UUID id) {
        return disclosureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Disclosure not found: " + id));
    }
}
