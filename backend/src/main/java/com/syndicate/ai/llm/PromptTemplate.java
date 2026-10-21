package com.syndicate.ai.llm;

import java.util.Map;

public record PromptTemplate(
        String name,
        String version,
        String templateText,
        String sha256Hash
) {
    public String render(Map<String, Object> variables) {
        String result = templateText;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                result = result.replace(placeholder, entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }
        return result;
    }
}
