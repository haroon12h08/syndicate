package com.syndicate.ai.suggestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactService;
import com.syndicate.candidatefact.CandidateFactStatus;
import com.syndicate.fact.FactKeyResolver;
import com.syndicate.fact.FactService;
import com.syndicate.fact.FactValueNormalizer;
import com.syndicate.fact.dto.FactDto;
import com.syndicate.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PreConflictDetector {

    private final CandidateFactService candidateFactService;
    private final FactService factService;
    private final FactKeyResolver factKeyResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PreConflictDetector(CandidateFactService candidateFactService,
                               FactService factService,
                               FactKeyResolver factKeyResolver) {
        this.candidateFactService = candidateFactService;
        this.factService = factService;
        this.factKeyResolver = factKeyResolver;
    }

    public List<AiSuggestion> detect(Transaction transaction, UUID callerId) {
        List<CandidateFact> pendingCandidates = candidateFactService
                .findByTransactionIdAndStatus(transaction.getId(), CandidateFactStatus.PENDING);

        if (pendingCandidates.isEmpty()) {
            return List.of();
        }

        List<FactDto> currentFacts = factService.listForTransaction(transaction.getId(), false, callerId);
        List<AiSuggestion> suggestions = new ArrayList<>();

        for (CandidateFact candidate : pendingCandidates) {
            String targetKey = factKeyResolver.resolve(null, candidate.getLabel());
            if (targetKey == null) continue;

            String candidateCanonical = FactValueNormalizer.canonical(candidate.getValue(), null);

            for (FactDto fact : currentFacts) {
                if (!targetKey.equalsIgnoreCase(fact.factKey())) continue;

                // Match periods if present
                boolean periodMatches = periodsOverlap(candidate.getPeriod(), fact.period());
                if (!periodMatches) continue;

                String factCanonical = FactValueNormalizer.canonical(fact.value(), fact.unit());

                if (!candidateCanonical.equals(factCanonical)) {
                    SuggestionSeverity severity = fact.status() == com.syndicate.fact.FactStatus.VERIFIED
                            ? SuggestionSeverity.BLOCKING
                            : SuggestionSeverity.WARNING;

                    String statusStr = fact.status() != null ? fact.status().name().toLowerCase() : "unverified";
                    String title = "Pre-Conflict: " + candidate.getLabel() + " diverges from " + statusStr + " fact";
                    String description = String.format(
                            "Candidate extracted value '%s' from %s contradicts %s fact '%s' (%s). " +
                            "Accepting this candidate into the active dataset will trigger an active conflict.",
                            candidate.getValue(),
                            candidate.getEvidence() != null ? candidate.getEvidence().getFileName() : "uploaded document",
                            statusStr,
                            fact.value() + (fact.unit() != null ? " " + fact.unit() : ""),
                            fact.factKey() + (fact.period() != null ? " @ " + fact.period() : "")
                    );

                    Map<String, Object> payload = Map.of(
                            "candidateFactId", candidate.getId().toString(),
                            "conflictingFactId", fact.id().toString(),
                            "factKey", targetKey,
                            "proposedValue", candidate.getValue(),
                            "existingValue", fact.value() + (fact.unit() != null ? " " + fact.unit() : ""),
                            "period", candidate.getPeriod() != null ? candidate.getPeriod() : ""
                    );

                    String payloadJson;
                    try {
                        payloadJson = objectMapper.writeValueAsString(payload);
                    } catch (Exception e) {
                        payloadJson = "{}";
                    }

                    suggestions.add(new AiSuggestion(
                            transaction,
                            candidate.getWorkstream(),
                            null,
                            SuggestionType.PRE_CONFLICT,
                            title,
                            description,
                            severity,
                            "CANDIDATE_FACT",
                            candidate.getId(),
                            "REJECT_CANDIDATE",
                            payloadJson
                    ));
                }
            }
        }

        return suggestions;
    }

    private boolean periodsOverlap(String p1, String p2) {
        if (p1 == null || p2 == null) return true;
        return p1.trim().equalsIgnoreCase(p2.trim());
    }
}
