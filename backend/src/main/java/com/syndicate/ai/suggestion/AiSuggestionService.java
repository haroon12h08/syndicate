package com.syndicate.ai.suggestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.ai.artifact.AiArtifactRepository;
import com.syndicate.ai.llm.LlmClient;
import com.syndicate.ai.llm.LlmPrompt;
import com.syndicate.ai.llm.LlmResponse;
import com.syndicate.ai.llm.PromptTemplate;
import com.syndicate.ai.llm.PromptTemplateLoader;
import com.syndicate.candidatefact.CandidateFactService;
import com.syndicate.candidatefact.dto.RejectCandidateFactRequest;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

    private final AiSuggestionRepository aiSuggestionRepository;
    private final AiArtifactRepository aiArtifactRepository;
    private final PreConflictDetector preConflictDetector;
    private final MissingEvidenceDetector missingEvidenceDetector;
    private final DiligenceGapDetector diligenceGapDetector;
    private final TransactionService transactionService;
    private final CandidateFactService candidateFactService;
    private final LlmClient llmClient;
    private final PromptTemplateLoader templateLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiSuggestionService(AiSuggestionRepository aiSuggestionRepository,
                               AiArtifactRepository aiArtifactRepository,
                               PreConflictDetector preConflictDetector,
                               MissingEvidenceDetector missingEvidenceDetector,
                               DiligenceGapDetector diligenceGapDetector,
                               TransactionService transactionService,
                               CandidateFactService candidateFactService,
                               LlmClient llmClient,
                               PromptTemplateLoader templateLoader) {
        this.aiSuggestionRepository = aiSuggestionRepository;
        this.aiArtifactRepository = aiArtifactRepository;
        this.preConflictDetector = preConflictDetector;
        this.missingEvidenceDetector = missingEvidenceDetector;
        this.diligenceGapDetector = diligenceGapDetector;
        this.transactionService = transactionService;
        this.candidateFactService = candidateFactService;
        this.llmClient = llmClient;
        this.templateLoader = templateLoader;
    }

    @Transactional(readOnly = true)
    public List<AiSuggestionDto> list(UUID transactionId, SuggestionStatus status, UUID callerId) {
        transactionService.requireMembership(transactionId, callerId);
        List<AiSuggestion> suggestions;
        if (status != null) {
            suggestions = aiSuggestionRepository.findByTransactionIdAndStatusOrderByCreatedAtDesc(transactionId, status);
        } else {
            suggestions = aiSuggestionRepository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
        }
        return suggestions.stream().map(AiSuggestionDto::from).toList();
    }

    public GenerateSuggestionsResponse generate(UUID transactionId, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);

        List<AiSuggestion> detected = new ArrayList<>();
        detected.addAll(preConflictDetector.detect(transaction, caller.getId()));
        detected.addAll(missingEvidenceDetector.detect(transaction));
        detected.addAll(diligenceGapDetector.detect(transaction));

        List<AiSuggestion> newSuggestions = new ArrayList<>();
        for (AiSuggestion suggestion : detected) {
            Optional<AiSuggestion> existing = aiSuggestionRepository
                    .findFirstByTransactionIdAndTargetTypeAndTargetIdAndSuggestionTypeAndStatus(
                            transactionId, suggestion.getTargetType(), suggestion.getTargetId(),
                            suggestion.getSuggestionType(), SuggestionStatus.PENDING
                    );
            if (existing.isEmpty()) {
                newSuggestions.add(suggestion);
            }
        }

        // Record AI Artifact for the generation run
        AiArtifact artifact = recordSuggestionArtifact(transaction, newSuggestions);
        if (artifact != null) {
            for (AiSuggestion s : newSuggestions) {
                s.setAiArtifact(artifact);
            }
        }

        List<AiSuggestion> saved = aiSuggestionRepository.saveAll(newSuggestions);
        long pendingCount = aiSuggestionRepository.countByTransactionIdAndStatus(transactionId, SuggestionStatus.PENDING);

        List<AiSuggestion> allPending = aiSuggestionRepository
                .findByTransactionIdAndStatusOrderByCreatedAtDesc(transactionId, SuggestionStatus.PENDING);

        return new GenerateSuggestionsResponse(
                saved.size(),
                (int) pendingCount,
                allPending.stream().map(AiSuggestionDto::from).toList(),
                artifact != null ? artifact.getId() : null
        );
    }

    public AiSuggestionDto accept(UUID transactionId, UUID suggestionId, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        AiSuggestion suggestion = findSuggestion(suggestionId, transactionId);

        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new IllegalStateException("Suggestion is not in PENDING status: " + suggestion.getStatus());
        }

        // Apply domain action if applicable
        if ("REJECT_CANDIDATE".equalsIgnoreCase(suggestion.getActionType())
                && "CANDIDATE_FACT".equalsIgnoreCase(suggestion.getTargetType())) {
            try {
                JsonNode payload = objectMapper.readTree(suggestion.getActionPayload());
                String candidateIdStr = payload.has("candidateFactId")
                        ? payload.get("candidateFactId").asText()
                        : suggestion.getTargetId().toString();
                UUID candidateId = UUID.fromString(candidateIdStr);
                candidateFactService.reject(candidateId,
                        new RejectCandidateFactRequest("Rejected based on AI pre-conflict suggestion: " + suggestion.getTitle()),
                        caller);
            } catch (Exception e) {
                log.warn("Failed to automatically reject candidate fact for suggestion {}: {}", suggestionId, e.getMessage());
            }
        }

        suggestion.accept(caller);
        return AiSuggestionDto.from(suggestion);
    }

    public AiSuggestionDto dismiss(UUID transactionId, UUID suggestionId, DismissSuggestionRequest request, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        AiSuggestion suggestion = findSuggestion(suggestionId, transactionId);

        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new IllegalStateException("Suggestion is not in PENDING status: " + suggestion.getStatus());
        }

        String reason = request != null && request.reason() != null ? request.reason() : "Dismissed by user";
        suggestion.dismiss(caller, reason);
        return AiSuggestionDto.from(suggestion);
    }

    private AiSuggestion findSuggestion(UUID suggestionId, UUID transactionId) {
        AiSuggestion s = aiSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found: " + suggestionId));
        if (!s.getTransaction().getId().equals(transactionId)) {
            throw new ResourceNotFoundException("Suggestion not found for transaction: " + suggestionId);
        }
        return s;
    }

    private AiArtifact recordSuggestionArtifact(Transaction transaction, List<AiSuggestion> suggestions) {
        try {
            PromptTemplate template = templateLoader.load("suggestion-generation", "v1");
            Map<String, Object> variables = Map.of(
                    "transactionName", transaction.getName(),
                    "candidateCount", suggestions.stream().filter(s -> s.getSuggestionType() == SuggestionType.PRE_CONFLICT).count(),
                    "evidenceCount", suggestions.stream().filter(s -> s.getSuggestionType() == SuggestionType.MISSING_EVIDENCE).count(),
                    "gapCount", suggestions.stream().filter(s -> s.getSuggestionType() == SuggestionType.DILIGENCE_GAP).count()
            );

            LlmPrompt prompt = LlmPrompt.builder()
                    .template(template)
                    .userPrompt(template.render(variables))
                    .inputEntityIds(List.of(transaction.getId()))
                    .inputHashes(Map.of("transaction_id", transaction.getId().toString()))
                    .temperature(0.0)
                    .build();

            LlmResponse response = llmClient.generate(prompt);

            String entityIdsJson = objectMapper.writeValueAsString(List.of(transaction.getId()));
            String hashesJson = objectMapper.writeValueAsString(Map.of("transaction_id", transaction.getId().toString()));

            String structuredJsonStr = null;
            try {
                JsonNode parsed = objectMapper.readTree(response.rawText());
                structuredJsonStr = parsed.toString();
            } catch (Exception ignored) {}

            AiArtifact artifact = new AiArtifact(
                    response.provider(),
                    response.model(),
                    response.modelVersion(),
                    prompt.templateName(),
                    prompt.templateVersion(),
                    prompt.templateHash(),
                    entityIdsJson,
                    hashesJson,
                    response.rawText(),
                    structuredJsonStr,
                    0.95,
                    response.promptTokens(),
                    response.completionTokens(),
                    response.totalCostUsd(),
                    response.durationMs()
            );

            return aiArtifactRepository.save(artifact);
        } catch (Exception e) {
            log.warn("Failed to generate AI artifact for suggestion run: {}", e.getMessage());
            return null;
        }
    }
}
