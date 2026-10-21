package com.syndicate.ai.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.ai.artifact.AiArtifact;
import com.syndicate.ai.artifact.AiArtifactRepository;
import com.syndicate.ai.llm.LlmClient;
import com.syndicate.ai.llm.LlmPrompt;
import com.syndicate.ai.llm.LlmResponse;
import com.syndicate.ai.llm.PromptTemplate;
import com.syndicate.ai.llm.PromptTemplateLoader;
import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.evidence.Evidence;
import com.syndicate.ingestion.DocumentToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiCandidateFactExtractor {

    private static final Logger log = LoggerFactory.getLogger(AiCandidateFactExtractor.class);

    private final PromptTemplateLoader templateLoader;
    private final LlmClient llmClient;
    private final AiArtifactRepository aiArtifactRepository;
    private final ObjectMapper objectMapper;

    public AiCandidateFactExtractor(PromptTemplateLoader templateLoader,
                                    LlmClient llmClient,
                                    AiArtifactRepository aiArtifactRepository,
                                    ObjectMapper objectMapper) {
        this.templateLoader = templateLoader;
        this.llmClient = llmClient;
        this.aiArtifactRepository = aiArtifactRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractedCandidateResult extract(Evidence evidence, List<DocumentToken> tokens,
                                            int defaultPageWidth, int defaultPageHeight) {
        PromptTemplate template = templateLoader.load("candidate-fact-extraction", "v1");

        String formattedContent = formatTokensByPage(tokens);
        if (formattedContent.length() > 8000) {
            formattedContent = formattedContent.substring(0, 8000) + "... [truncated]";
        }

        String renderedUserPrompt = template.render(Map.of(
                "fileName", evidence.getFileName(),
                "documentType", evidence.getDocumentType() != null ? evidence.getDocumentType().name() : "OTHER",
                "content", formattedContent
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

        List<CandidateFact> candidates = new ArrayList<>();
        String structuredJson = null;
        Double aggregateConfidence = 0.90;

        try {
            JsonNode root = objectMapper.readTree(response.rawText());
            structuredJson = objectMapper.writeValueAsString(root);
            JsonNode candidatesArr = root.path("candidates");

            if (candidatesArr.isArray()) {
                double totalConf = 0;
                int count = 0;

                for (JsonNode item : candidatesArr) {
                    String label = item.path("label").asText("");
                    String value = item.path("value").asText("");
                    String period = item.path("period").asText(null);
                    int pageNumber = item.path("pageNumber").asInt(1);
                    double conf = item.has("confidence") ? item.path("confidence").asDouble(0.9) : 0.9;
                    double bboxX = item.path("bboxX").asDouble(100.0);
                    double bboxY = item.path("bboxY").asDouble(200.0);
                    double bboxWidth = item.path("bboxWidth").asDouble(80.0);
                    double bboxHeight = item.path("bboxHeight").asDouble(20.0);

                    // Refine coordinates if matching token found on that page
                    DocumentToken matchedToken = findTokenMatchingValue(tokens, pageNumber, value);
                    int imgWidth = defaultPageWidth;
                    int imgHeight = defaultPageHeight;
                    if (matchedToken != null) {
                        bboxX = matchedToken.x();
                        bboxY = matchedToken.y();
                        bboxWidth = matchedToken.width();
                        bboxHeight = matchedToken.height();
                        imgWidth = matchedToken.pageImageWidth();
                        imgHeight = matchedToken.pageImageHeight();
                    }

                    CandidateFact cf = new CandidateFact(
                            evidence.getWorkstream(), evidence, label, value, period,
                            pageNumber, bboxX, bboxY, bboxWidth, bboxHeight,
                            imgWidth, imgHeight, CandidateFactSource.AI,
                            null, conf
                    );
                    candidates.add(cf);
                    totalConf += conf;
                    count++;
                }

                if (count > 0) {
                    aggregateConfidence = totalConf / count;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse extracted candidates JSON: {}", e.getMessage());
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
                aggregateConfidence,
                response.promptTokens(),
                response.completionTokens(),
                response.totalCostUsd(),
                response.durationMs()
        );

        AiArtifact savedArtifact = aiArtifactRepository.save(artifact);

        for (CandidateFact cf : candidates) {
            cf.setAiArtifact(savedArtifact);
        }

        return new ExtractedCandidateResult(candidates, savedArtifact);
    }

    private DocumentToken findTokenMatchingValue(List<DocumentToken> tokens, int pageNumber, String value) {
        if (tokens == null || tokens.isEmpty() || value == null || value.isBlank()) {
            return null;
        }
        String cleanVal = value.replaceAll("[^0.9a-zA-Z]", "").toLowerCase();
        return tokens.stream()
                .filter(t -> t.pageNumber() == pageNumber)
                .filter(t -> {
                    String cleanTok = t.text().replaceAll("[^0.9a-zA-Z]", "").toLowerCase();
                    return cleanTok.contains(cleanVal) || cleanVal.contains(cleanTok);
                })
                .findFirst()
                .orElse(null);
    }

    private String formatTokensByPage(List<DocumentToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        Map<Integer, List<DocumentToken>> byPage = tokens.stream()
                .collect(Collectors.groupingBy(DocumentToken::pageNumber));

        StringBuilder sb = new StringBuilder();
        byPage.keySet().stream().sorted().forEach(pageNum -> {
            sb.append("--- PAGE ").append(pageNum).append(" ---\n");
            List<DocumentToken> pageTokens = byPage.get(pageNum).stream()
                    .sorted(Comparator.comparingDouble(DocumentToken::y).thenComparingDouble(DocumentToken::x))
                    .toList();
            for (DocumentToken tok : pageTokens) {
                sb.append(tok.text()).append(" ");
            }
            sb.append("\n\n");
        });
        return sb.toString();
    }
}
