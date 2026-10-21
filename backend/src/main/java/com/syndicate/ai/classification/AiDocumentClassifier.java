package com.syndicate.ai.classification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.ai.artifact.AiArtifactRepository;
import com.syndicate.ai.llm.LlmClient;
import com.syndicate.ai.llm.LlmPrompt;
import com.syndicate.ai.llm.LlmResponse;
import com.syndicate.ai.llm.PromptTemplate;
import com.syndicate.ai.llm.PromptTemplateLoader;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AiDocumentClassifier {

    private static final Logger log = LoggerFactory.getLogger(AiDocumentClassifier.class);

    private final PromptTemplateLoader templateLoader;
    private final LlmClient llmClient;
    private final AiArtifactRepository aiArtifactRepository;
    private final ObjectMapper objectMapper;

    public AiDocumentClassifier(PromptTemplateLoader templateLoader,
                                LlmClient llmClient,
                                AiArtifactRepository aiArtifactRepository,
                                ObjectMapper objectMapper) {
        this.templateLoader = templateLoader;
        this.llmClient = llmClient;
        this.aiArtifactRepository = aiArtifactRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentClassificationResult classify(Evidence evidence, String extractedText) {
        PromptTemplate template = templateLoader.load("document-classification", "v1");

        String snippet = extractedText != null ? extractedText : "";
        if (snippet.length() > 6000) {
            snippet = snippet.substring(0, 6000) + "... [truncated]";
        }

        String renderedUserPrompt = template.render(Map.of(
                "fileName", evidence.getFileName(),
                "content", snippet
        ));

        Map<String, Object> inputHashes = Map.of(
                "file_sha256", evidence.getFileSha256() != null ? evidence.getFileSha256() : ""
        );

        LlmPrompt prompt = LlmPrompt.builder()
                .template(template)
                .userPrompt(renderedUserPrompt)
                .inputEntityIds(List.of(evidence.getId()))
                .inputHashes(inputHashes)
                .temperature(0.0)
                .build();

        LlmResponse response = llmClient.generate(prompt);

        EvidenceDocumentType docType = EvidenceDocumentType.OTHER;
        Double confidence = 0.5;
        String reasoning = "";
        String structuredJson = null;

        try {
            JsonNode root = objectMapper.readTree(response.rawText());
            structuredJson = objectMapper.writeValueAsString(root);
            String docTypeStr = root.path("documentType").asText("OTHER");
            try {
                docType = EvidenceDocumentType.valueOf(docTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown document type from AI: {}, defaulting to OTHER", docTypeStr);
                docType = EvidenceDocumentType.OTHER;
            }
            if (root.has("confidence")) {
                confidence = root.path("confidence").asDouble();
            }
            if (root.has("reasoning")) {
                reasoning = root.path("reasoning").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse classification JSON: {}", e.getMessage());
            structuredJson = "{}";
        }

        String entityIdsJson = "[]";
        String hashesJson = "{}";
        try {
            entityIdsJson = objectMapper.writeValueAsString(List.of(evidence.getId()));
            hashesJson = objectMapper.writeValueAsString(inputHashes);
        } catch (Exception ignored) {
        }

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
                structuredJson,
                confidence,
                response.promptTokens(),
                response.completionTokens(),
                response.totalCostUsd(),
                response.durationMs()
        );

        AiArtifact savedArtifact = aiArtifactRepository.save(artifact);

        evidence.setSuggestedDocumentType(docType.name());
        evidence.setClassificationAiArtifact(savedArtifact);

        return new DocumentClassificationResult(docType, confidence, reasoning, savedArtifact);
    }
}
