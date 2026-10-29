package com.syndicate.ai.suggestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.diligence.DiligenceQuestion;
import com.syndicate.diligence.DiligenceQuestionRepository;
import com.syndicate.diligence.DiligenceStatus;
import com.syndicate.issue.IssueSeverity;
import com.syndicate.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DiligenceGapDetector {

    private final DiligenceQuestionRepository diligenceQuestionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiligenceGapDetector(DiligenceQuestionRepository diligenceQuestionRepository) {
        this.diligenceQuestionRepository = diligenceQuestionRepository;
    }

    public List<AiSuggestion> detect(Transaction transaction) {
        List<DiligenceQuestion> questions = diligenceQuestionRepository
                .findByTransactionIdOrderByWorkstreamTypeAscCreatedAtAsc(transaction.getId());

        List<AiSuggestion> suggestions = new ArrayList<>();

        for (DiligenceQuestion dq : questions) {
            // Gap 1: Blocking or High diligence questions that are still open
            if (dq.getStatus() == DiligenceStatus.OPEN &&
                    (dq.getSeverity() == IssueSeverity.BLOCKING || dq.getSeverity() == IssueSeverity.HIGH)) {

                String title = "Diligence Gap: Unresolved " + dq.getSeverity() + " Question (" + (dq.getCode() != null ? dq.getCode() : dq.getWorkstreamType()) + ")";
                String description = String.format(
                        "Diligence question '%s' in category %s is marked %s and remains open. " +
                        "Regulatory review requires an answer supported by primary evidence before DRHP filing.",
                        dq.getQuestion(), dq.getWorkstreamType(), dq.getSeverity()
                );

                Map<String, Object> payload = Map.of(
                        "questionId", dq.getId().toString(),
                        "code", dq.getCode() != null ? dq.getCode() : "",
                        "category", dq.getWorkstreamType(),
                        "severity", dq.getSeverity().name()
                );

                String payloadJson;
                try {
                    payloadJson = objectMapper.writeValueAsString(payload);
                } catch (Exception e) {
                    payloadJson = "{}";
                }

                suggestions.add(new AiSuggestion(
                        transaction,
                        null,
                        null,
                        SuggestionType.DILIGENCE_GAP,
                        title,
                        description,
                        dq.getSeverity() == IssueSeverity.BLOCKING ? SuggestionSeverity.BLOCKING : SuggestionSeverity.WARNING,
                        "DILIGENCE_QUESTION",
                        dq.getId(),
                        "RESOLVE_GAP",
                        payloadJson
                ));
            }
        }

        return suggestions;
    }
}
