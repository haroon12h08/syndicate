package com.syndicate.provenance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.drhp.DrhpDocument;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.evidence.Evidence;
import com.syndicate.fact.Fact;
import com.syndicate.provenance.dto.ProvenanceLeafDto;
import com.syndicate.provenance.dto.ProvenanceManifestDto;
import com.syndicate.transaction.TransactionApprovalSignature;
import com.syndicate.transaction.TransactionApprovalSignatureRepository;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds and reads the cryptographic provenance behind a filing (§26, §60).
 *
 * <p>A leaf exists for every fact version printed, every evidence file behind those facts, and
 * every approval signature authorising the transition. Leaves carry the fact's <em>version</em>
 * and value, not just its id, so superseding a fact after filing changes the root — which is the
 * whole point: the manifest proves which state the document was built from.
 */
@Service
public class ProvenanceService {

    private final FilingProvenanceManifestRepository manifestRepository;
    private final DrhpDocumentRepository drhpRepository;
    private final TransactionApprovalSignatureRepository signatureRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProvenanceService(FilingProvenanceManifestRepository manifestRepository,
                              DrhpDocumentRepository drhpRepository,
                              TransactionApprovalSignatureRepository signatureRepository,
                              TransactionService transactionService) {
        this.manifestRepository = manifestRepository;
        this.drhpRepository = drhpRepository;
        this.signatureRepository = signatureRepository;
        this.transactionService = transactionService;
    }

    /** Canonical, sortable description of one input. Changing any part changes the root. */
    private record Leaf(String type, UUID referenceId, String label, String canonical) {
    }

    @Transactional
    public FilingProvenanceManifest createManifest(DrhpDocument document, User compiledBy) {
        List<Leaf> leaves = new ArrayList<>();

        for (Fact fact : document.getCitedFacts()) {
            leaves.add(new Leaf("FACT", fact.getId(), fact.getLabel(),
                    String.join("|", "FACT", fact.getId().toString(), "v" + fact.getVersion(),
                            fact.getLabel(), String.valueOf(fact.getValue()),
                            String.valueOf(fact.getUnit()), String.valueOf(fact.getStatus()),
                            String.valueOf(fact.getVerifiedAt()))));
        }

        Set<Evidence> evidence = new LinkedHashSet<>();
        document.getCitedFacts().forEach(f -> evidence.addAll(f.getEvidence()));
        for (Evidence item : evidence) {
            leaves.add(new Leaf("EVIDENCE", item.getId(), item.getFileName(),
                    String.join("|", "EVIDENCE", item.getId().toString(), item.getFileName(),
                            String.valueOf(item.getFileSha256()))));
        }

        List<TransactionApprovalSignature> signatures =
                signatureRepository.findByTransactionIdAndTransition(
                        document.getTransaction().getId(), "DRAFT_TO_ACTIVE");
        for (TransactionApprovalSignature signature : signatures) {
            leaves.add(new Leaf("APPROVAL", signature.getId(),
                    signature.getRequiredRole().name(),
                    String.join("|", "APPROVAL", signature.getId().toString(),
                            signature.getTransition(), signature.getRequiredRole().name(),
                            signature.getSignedByUser().getId().toString(),
                            String.valueOf(signature.getSignedAt()))));
        }

        // Sorting makes the root independent of collection iteration order.
        leaves.sort(Comparator.comparing(Leaf::canonical));

        List<String> hashes = leaves.stream().map(l -> MerkleTree.sha256Hex(l.canonical())).toList();
        MerkleTree tree = new MerkleTree(hashes);

        String serialisedLeaves;
        try {
            serialisedLeaves = objectMapper.writeValueAsString(leaves);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise provenance leaves: " + e.getMessage(), e);
        }

        FilingProvenanceManifest manifest = new FilingProvenanceManifest(document, tree.root(),
                leaves.size(), serialisedLeaves, compiledBy);
        return manifestRepository.save(manifest);
    }

    @Transactional(readOnly = true)
    public ProvenanceManifestDto get(UUID drhpDocumentId, UUID callerId) {
        DrhpDocument document = drhpRepository.findById(drhpDocumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + drhpDocumentId));
        transactionService.requireMembership(document.getTransaction().getId(), callerId);

        FilingProvenanceManifest manifest = manifestRepository.findByDrhpDocumentId(drhpDocumentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This document was not compiled as a final filing, so it has no provenance manifest"));

        List<Leaf> leaves;
        try {
            leaves = objectMapper.readValue(manifest.getLeaves(), new TypeReference<List<Leaf>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Could not read provenance leaves: " + e.getMessage(), e);
        }

        List<String> hashes = leaves.stream().map(l -> MerkleTree.sha256Hex(l.canonical())).toList();
        MerkleTree tree = new MerkleTree(hashes);

        List<ProvenanceLeafDto> leafDtos = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i++) {
            Leaf leaf = leaves.get(i);
            List<ProvenanceLeafDto.ProofStepDto> proof = tree.proofFor(i).stream()
                    .map(s -> new ProvenanceLeafDto.ProofStepDto(s.siblingHash(), s.position()))
                    .toList();
            leafDtos.add(new ProvenanceLeafDto(i, leaf.type(), leaf.referenceId(), leaf.label(),
                    leaf.canonical(), hashes.get(i), proof));
        }

        // Recomputing here means the response proves itself rather than asserting a stored value.
        boolean verified = tree.root().equals(manifest.getMerkleRoot());

        return new ProvenanceManifestDto(manifest.getId(), document.getId(), document.getVersion(),
                document.getCompileMode().name(), manifest.getMerkleRoot(), manifest.getAlgorithm(),
                manifest.getLeafCount(), manifest.getCompiledAt(),
                manifest.getCompiledByUser() != null ? manifest.getCompiledByUser().getFullName() : null,
                leafDtos, verified);
    }
}
