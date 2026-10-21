package com.syndicate.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final RestClient restClient;
    private final String model;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicLlmClient(@Value("${syndicate.ai.anthropic.api-key:}") String apiKey,
                              @Value("${syndicate.ai.anthropic.model:claude-3-5-sonnet-20241022}") String model,
                              @Value("${syndicate.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public LlmResponse generate(LlmPrompt prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Anthropic API key is not configured");
        }

        long start = System.currentTimeMillis();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", prompt.maxTokens() != null ? prompt.maxTokens() : 2048,
                "temperature", prompt.temperature() != null ? prompt.temperature() : 0.0,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt.userPrompt())
                )
        );

        try {
            String responseStr = restClient.post()
                    .uri("/v1/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            long duration = Math.max(1, System.currentTimeMillis() - start);
            JsonNode root = objectMapper.readTree(responseStr);

            String text = "";
            JsonNode contentArr = root.path("content");
            if (contentArr.isArray() && !contentArr.isEmpty()) {
                text = contentArr.get(0).path("text").asText("");
            }

            int promptTokens = root.path("usage").path("input_tokens").asInt(0);
            int completionTokens = root.path("usage").path("output_tokens").asInt(0);

            // Claude 3.5 Sonnet: $3 per million input tokens, $15 per million output tokens
            BigDecimal cost = BigDecimal.valueOf(0.000003).multiply(BigDecimal.valueOf(promptTokens))
                    .add(BigDecimal.valueOf(0.000015).multiply(BigDecimal.valueOf(completionTokens)));

            return new LlmResponse(text, promptTokens, completionTokens, cost, duration,
                    getProvider(), getModel(), getModelVersion());

        } catch (Exception e) {
            log.error("Failed to call Anthropic API for model {}: {}", model, e.getMessage(), e);
            throw new RuntimeException("Anthropic generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProvider() {
        return "anthropic";
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String getModelVersion() {
        return "20241022";
    }
}
