package com.syndicate.graph;

import com.syndicate.drhp.DisclosureRepository;
import com.syndicate.drhp.DrhpDocumentRepository;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.filing.DocumentApprovalRepository;
import com.syndicate.review.ReviewRepository;
import com.syndicate.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a change to one fact would reach (spec §6).
 *
 * <p>Shown before the change is made, so the consequences are a decision rather than a surprise.
 * Every entry comes from a persisted dependency, never from an assumption about what usually
 * depends on what.
 */
@Service
public class ImpactService {

    /** One affected thing, named the way its owner would name it. */
    public record ImpactEntry(GraphNodeType type, UUID id, String title, String consequence) {
    }

    public record ImpactSet(UUID factId, String factLabel, String currentValue, List<ImpactEntry> affected) {
        public boolean isEmpty() {
            return affected.isEmpty();
        }
    }

    private final DependencyGraphService graph;
    private final FactRepository factRepository;
    private final DisclosureRepository disclosureRepository;
    private final DrhpDocumentRepository documentRepository;
    private final ReviewRepository reviewRepository;
    private final DocumentApprovalRepository approvalRepository;
    private final TransactionService transactionService;

    public ImpactService(DependencyGraphService graph, FactRepository factRepository,
                         DisclosureRepository disclosureRepository, DrhpDocumentRepository documentRepository,
                         ReviewRepository reviewRepository, DocumentApprovalRepository approvalRepository,
                         TransactionService transactionService) {
        this.graph = graph;
        this.factRepository = factRepository;
        this.disclosureRepository = disclosureRepository;
        this.documentRepository = documentRepository;
        this.reviewRepository = reviewRepository;
        this.approvalRepository = approvalRepository;
        this.transactionService = transactionService;
    }

    @Transactional(readOnly = true)
    public ImpactSet forFact(UUID factId, UUID callerId) {
        Fact fact = factRepository.findById(factId)
                .orElseThrow(() -> new com.syndicate.common.ResourceNotFoundException("Fact not found: " + factId));
        UUID transactionId = fact.getWorkstream().getTransaction().getId();
        transactionService.requireMembership(transactionId, callerId);

        List<ImpactEntry> affected = new ArrayList<>();
        for (ImpactedNode node : graph.impactOf(transactionId, new GraphNode(GraphNodeType.FACT, fact.getLineageId()))) {
            switch (node.type()) {
                case DISCLOSURE -> disclosureRepository.findById(node.id()).ifPresent(d -> affected.add(
                        new ImpactEntry(node.type(), d.getId(), d.getTitle(),
                                "would need re-reading against the new value")));
                case DOCUMENT -> documentRepository.findById(node.id()).ifPresent(doc -> affected.add(
                        new ImpactEntry(node.type(), doc.getId(), "DRHP v" + doc.getVersion(),
                                "would no longer match the record and would have to be recompiled")));
                case REVIEW -> reviewRepository.findById(node.id()).ifPresent(review -> affected.add(
                        new ImpactEntry(node.type(), review.getId(),
                                "Review by " + review.getReviewer().getFullName(),
                                "would stop covering this value and would have to be redone")));
                case APPROVAL -> approvalRepository.findById(node.id()).ifPresent(approval -> affected.add(
                        new ImpactEntry(node.type(), approval.getId(),
                                "Approval by " + approval.getApprover().getFullName(),
                                "would be invalidated")));
                default -> { }
            }
        }
        return new ImpactSet(fact.getId(), fact.getLabel(), fact.getValue(), affected);
    }
}
