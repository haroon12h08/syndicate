package com.syndicate.ai.llm;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LlmPrompt(
        String templateName,
        String templateVersion,
        String templateHash,
        String systemPrompt,
        String userPrompt,
        List<UUID> inputEntityIds,
        Map<String, Object> inputHashes,
        Double temperature,
        Integer maxTokens
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String templateName;
        private String templateVersion;
        private String templateHash;
        private String systemPrompt;
        private String userPrompt;
        private List<UUID> inputEntityIds = List.of();
        private Map<String, Object> inputHashes = Map.of();
        private Double temperature = 0.0;
        private Integer maxTokens = 2048;

        public Builder template(PromptTemplate template) {
            this.templateName = template.name();
            this.templateVersion = template.version();
            this.templateHash = template.sha256Hash();
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder inputEntityIds(List<UUID> inputEntityIds) {
            this.inputEntityIds = inputEntityIds != null ? inputEntityIds : List.of();
            return this;
        }

        public Builder inputHashes(Map<String, Object> inputHashes) {
            this.inputHashes = inputHashes != null ? inputHashes : Map.of();
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public LlmPrompt build() {
            return new LlmPrompt(templateName, templateVersion, templateHash, systemPrompt, userPrompt,
                    inputEntityIds, inputHashes, temperature, maxTokens);
        }
    }
}
