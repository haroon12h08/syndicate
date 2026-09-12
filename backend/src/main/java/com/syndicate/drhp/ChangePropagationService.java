package com.syndicate.drhp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Walks the dependency DAG downstream of a changed fact.
 *
 * <p>The invariant: a superseded fact must never leave stale derived work silently in place.
 * Disclosures built from it need a human re-read, and any DRHP that printed its value no longer
 * matches the record and is invalidated.
 */
@Service
public class ChangePropagationService {

    private static final Logger log = LoggerFactory.getLogger(ChangePropagationService.class);

    private final DisclosureRepository disclosureRepository;
    private final DrhpDocumentRepository drhpRepository;

    public ChangePropagationService(DisclosureRepository disclosureRepository,
                                     DrhpDocumentRepository drhpRepository) {
        this.disclosureRepository = disclosureRepository;
        this.drhpRepository = drhpRepository;
    }

    /**
     * Runs after the fact change commits, in its own transaction — the same reason readiness
     * re-evaluation does: joining the completing transaction would discard these writes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void propagateFactSuperseded(UUID supersededFactId, String factLabel) {
        try {
            List<Disclosure> dependents = disclosureRepository.findBySourceFactId(supersededFactId);
            for (Disclosure disclosure : dependents) {
                disclosure.markStale("Source fact \"" + factLabel + "\" was superseded.");
            }

            List<DrhpDocument> citing = drhpRepository.findCompiledCitingFact(supersededFactId);
            for (DrhpDocument document : citing) {
                document.invalidate("Cited fact \"" + factLabel + "\" was superseded after compilation.");
            }

            if (!dependents.isEmpty() || !citing.isEmpty()) {
                log.info("Fact {} superseded: marked {} disclosure(s) stale, invalidated {} DRHP document(s)",
                        supersededFactId, dependents.size(), citing.size());
            }
        } catch (Exception e) {
            log.warn("Change propagation failed for fact {}: {}", supersededFactId, e.toString());
        }
    }
}
