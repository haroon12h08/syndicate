package com.syndicate.ai.llm;

public interface LlmClient {

    LlmResponse generate(LlmPrompt prompt);

    String getProvider();

    String getModel();

    default String getModelVersion() {
        return null;
    }
}
