package com.syndicate.drhp;

import com.syndicate.graph.DependencyGraphService;
import com.syndicate.graph.GraphNode;
import com.syndicate.graph.GraphNodeType;
import com.syndicate.graph.ImpactedNode;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Marks everything downstream of a changed fact as stale, using the persisted dependency graph.
 *
 * <p>The invariant: a superseded fact must never leave stale derived work looking current.
 * This runs inside the transaction that records the change, so the change and its consequences
 * commit together or not at all.
 */
@Service
public class ChangePropagationService {

    private static final Logger log = LoggerFactory.getLogger(ChangePropagationService.class);

    private final DependencyGraphService graph;
    private final DisclosureRepository disclosureRepository;
    private final DrhpDocumentRepository drhpRepository;
    private final EntityManager entityManager;

    public ChangePropagationService(DependencyGraphService graph, DisclosureRepository disclosureRepository,
                                    DrhpDocumentRepository drhpRepository, EntityManager entityManager) {
        this.graph = graph;
        this.disclosureRepository = disclosureRepository;
        this.drhpRepository = drhpRepository;
        this.entityManager = entityManager;
    }

    /** @return the dependents that were found downstream of the fact */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ImpactedNode> propagateFactSuperseded(UUID transactionId, UUID factLineageId, String factLabel) {
        return propagateFactChange(transactionId, factLineageId, "Source fact \"" + factLabel + "\" was superseded.");
    }

    /** Marks every current dependent of the fact lineage stale, recording {@code reason}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ImpactedNode> propagateFactChange(UUID transactionId, UUID factLineageId, String reason) {
        // the graph is read with SQL, so pending JPA changes (the new version) must be visible first
        entityManager.flush();
        List<ImpactedNode> impacted = graph.impactOf(transactionId, new GraphNode(GraphNodeType.FACT, factLineageId));
        int disclosures = 0;
        int documents = 0;
        for (ImpactedNode node : impacted) {
            if (node.type() == GraphNodeType.DISCLOSURE) {
                disclosureRepository.findById(node.id()).ifPresent(d -> d.markStale(reason));
                disclosures++;
            } else if (node.type() == GraphNodeType.DOCUMENT) {
                drhpRepository.findById(node.id())
                        .filter(doc -> doc.getStatus() == DrhpStatus.COMPILED)
                        .ifPresent(doc -> doc.invalidate(reason + " (after compilation)"));
                documents++;
            }
        }
        if (!impacted.isEmpty()) {
            log.info("Fact lineage {} changed: {} disclosure(s) stale, {} DRHP document(s) invalidated",
                    factLineageId, disclosures, documents);
        }
        return impacted;
    }
}
