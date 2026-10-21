package com.syndicate.ai.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LlmConfiguration {

    @Bean
    @ConditionalOnProperty(name = "syndicate.ai.provider", havingValue = "anthropic")
    @Primary
    public LlmClient anthropicLlmClient(@Value("${syndicate.ai.anthropic.api-key:}") String apiKey,
                                       @Value("${syndicate.ai.anthropic.model:claude-3-5-sonnet-20241022}") String model,
                                       @Value("${syndicate.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl) {
        return new AnthropicLlmClient(apiKey, model, baseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "syndicate.ai.provider", havingValue = "mock", matchIfMissing = true)
    @Primary
    public LlmClient primaryMockLlmClient(MockLlmClient mockLlmClient) {
        return mockLlmClient;
    }
}
