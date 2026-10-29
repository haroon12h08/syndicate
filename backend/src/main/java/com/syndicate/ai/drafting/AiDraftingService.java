package com.syndicate.ai.drafting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.ai.artifact.AiArtifactRepository;
import com.syndicate.ai.drafting.dto.*;
import com.syndicate.ai.llm.*;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.fact.FactService;
import com.syndicate.fact.dto.FactDto;
import com.syndicate.observation.ObservationService;
import com.syndicate.observation.dto.ObservationDto;
import com.syndicate.transaction.Transaction;
import com.syndicate.transaction.TransactionService;
import com.syndicate.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AiDraftingService {

    private static final Logger log = LoggerFactory.getLogger(AiDraftingService.class);

    private final AiArtifactRepository aiArtifactRepository;
    private final LlmClient llmClient;
    private final PromptTemplateLoader templateLoader;
    private final FactService factService;
    private final EvidenceRepository evidenceRepository;
    private final ObservationService observationService;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiDraftingService(AiArtifactRepository aiArtifactRepository,
                             LlmClient llmClient,
                             PromptTemplateLoader templateLoader,
                             FactService factService,
                             EvidenceRepository evidenceRepository,
                             ObservationService observationService,
                             TransactionService transactionService) {
        this.aiArtifactRepository = aiArtifactRepository;
        this.llmClient = llmClient;
        this.templateLoader = templateLoader;
        this.factService = factService;
        this.evidenceRepository = evidenceRepository;
        this.observationService = observationService;
        this.transactionService = transactionService;
    }

    public DraftDisclosureResponse draftDisclosure(UUID transactionId, DraftDisclosureRequest request, User caller) {
        transactionService.requireMembership(transactionId, caller.getId());
        Transaction transaction = transactionService.findTransaction(transactionId);

        List<FactDto> facts = factService.listForTransaction(transactionId, false, caller.getId());

        StringBuilder factsText = new StringBuilder();
        for (FactDto f : facts) {
            factsText.append(String.format("- %s = %s%s (Period: %s, Status: %s)\n",
                    f.factKey(), f.value(), f.unit() != null ? " " + f.unit() : "",
                    f.period() != null ? f.period() : "N/A", f.status()));
        }

        PromptTemplate template = templateLoader.load("disclosure-drafting", "v1");
        Map<String, Object> variables = Map.of(
                "transactionName", transaction.getName(),
                "sectionCode", request.sectionCode() != null ? request.sectionCode() : "GENERAL",
                "title", request.title() != null ? request.title() : "",
                "userInstructions", request.userInstructions() != null ? request.userInstructions() : "Draft complete disclosure section",
                "availableFacts", factsText.toString()
        );

        LlmPrompt prompt = LlmPrompt.builder()
                .template(template)
                .userPrompt(template.render(variables))
                .inputEntityIds(List.of(transaction.getId()))
                .inputHashes(Map.of("transaction_id", transaction.getId().toString()))
                .temperature(0.1)
                .build();

        LlmResponse response = llmClient.generate(prompt);

        String suggestedTitle = request.title() != null ? request.title() : "Disclosure";
        String bodyTemplate = "";
        List<String> citedKeys = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(response.rawText());
            if (root.has("suggestedTitle")) {
                suggestedTitle = root.get("suggestedTitle").asText();
            }
            if (root.has("bodyTemplate")) {
                bodyTemplate = root.get("bodyTemplate").asText();
            }
            if (root.has("citedFactKeys") && root.get("citedFactKeys").isArray()) {
                for (JsonNode k : root.get("citedFactKeys")) {
                    citedKeys.add(k.asText());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse disclosure drafting response: {}", e.getMessage());
            bodyTemplate = response.rawText();
        }

        AiArtifact artifact = saveArtifact(prompt, response, "DisclosureDraft", null);

        return new DraftDisclosureResponse(
                request.sectionCode(),
                suggestedTitle,
                bodyTemplate,
                citedKeys,
                artifact != null ? artifact.getId() : null
        );
    }

    public DraftObservationResponse draftObservationResponse(UUID observationId, DraftObservationResponseRequest request, User caller) {
        ObservationDto observation = observationService.get(observationId, caller.getId());
        UUID txId = observation.transactionId();

        List<FactDto> facts = factService.listForTransaction(txId, false, caller.getId());
        List<Evidence> evidenceList = evidenceRepository.findByWorkstreamTransactionIdOrderByUploadedAtDesc(txId);

        StringBuilder factsText = new StringBuilder();
        Map<String, UUID> factKeyToId = new HashMap<>();
        for (FactDto f : facts) {
            factsText.append(String.format("- [ID: %s] %s = %s (%s)\n", f.id(), f.factKey(), f.value(), f.period()));
            factKeyToId.put(f.factKey().toLowerCase(), f.id());
        }

        StringBuilder evidenceText = new StringBuilder();
        for (Evidence e : evidenceList) {
            evidenceText.append(String.format("- [ID: %s] %s (%s)\n", e.getId(), e.getFileName(), e.getDocumentType()));
        }

        PromptTemplate template = templateLoader.load("observation-response", "v1");
        Map<String, Object> variables = Map.of(
                "authority", observation.authority(),
                "reference", observation.reference() != null ? observation.reference() : "N/A",
                "sectionCode", observation.sectionCode() != null ? observation.sectionCode() : "GENERAL",
                "observationText", observation.observation(),
                "userInstructions", request != null && request.userInstructions() != null ? request.userInstructions() : "",
                "availableFacts", factsText.toString(),
                "availableEvidence", evidenceText.toString()
        );

        LlmPrompt prompt = LlmPrompt.builder()
                .template(template)
                .userPrompt(template.render(variables))
                .inputEntityIds(List.of(observationId))
                .inputHashes(Map.of("observation_id", observationId.toString()))
                .temperature(0.1)
                .build();

        LlmResponse response = llmClient.generate(prompt);

        String suggestedResponse = "";
        List<UUID> suggestedFactIds = new ArrayList<>();
        List<UUID> suggestedEvidenceIds = new ArrayList<>();
        List<UUID> suggestedDisclosureIds = new ArrayList<>();
        String reasoning = "";

        try {
            JsonNode root = objectMapper.readTree(response.rawText());
            if (root.has("suggestedResponse")) suggestedResponse = root.get("suggestedResponse").asText();
            if (root.has("reasoning")) reasoning = root.get("reasoning").asText();

            if (root.has("suggestedFactIds") && root.get("suggestedFactIds").isArray()) {
                for (JsonNode n : root.get("suggestedFactIds")) {
                    try { suggestedFactIds.add(UUID.fromString(n.asText())); } catch (Exception ignored) {}
                }
            }
            if (root.has("suggestedEvidenceIds") && root.get("suggestedEvidenceIds").isArray()) {
                for (JsonNode n : root.get("suggestedEvidenceIds")) {
                    try { suggestedEvidenceIds.add(UUID.fromString(n.asText())); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse observation draft response: {}", e.getMessage());
            suggestedResponse = response.rawText();
        }

        // If model didn't return explicit IDs but referenced facts or evidence exists, auto-ground with relevant items
        if (suggestedFactIds.isEmpty() && !facts.isEmpty()) {
            for (FactDto f : facts) {
                if (suggestedResponse.contains(f.factKey()) || observation.observation().toLowerCase().contains(f.label().toLowerCase())) {
                    suggestedFactIds.add(f.id());
                }
            }
            if (suggestedFactIds.isEmpty()) {
                suggestedFactIds.add(facts.get(0).id());
            }
        }

        if (suggestedEvidenceIds.isEmpty() && !evidenceList.isEmpty()) {
            suggestedEvidenceIds.add(evidenceList.get(0).getId());
        }

        AiArtifact artifact = saveArtifact(prompt, response, "RegulatoryObservationResponse", observationId);

        return new DraftObservationResponse(
                observationId,
                suggestedResponse,
                suggestedFactIds,
                suggestedEvidenceIds,
                suggestedDisclosureIds,
                reasoning,
                artifact != null ? artifact.getId() : null
        );
    }

    private AiArtifact saveArtifact(LlmPrompt prompt, LlmResponse response, String entityType, UUID entityId) {
        try {
            String entityIdsJson = objectMapper.writeValueAsString(prompt.inputEntityIds());
            String hashesJson = objectMapper.writeValueAsString(prompt.inputHashes());

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

            artifact.setResultingEntityType(entityType);
            artifact.setResultingEntityId(entityId);
            return aiArtifactRepository.save(artifact);
        } catch (Exception e) {
            log.warn("Failed to save AI artifact for drafting: {}", e.getMessage());
            return null;
        }
    }
}
