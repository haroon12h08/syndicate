package com.syndicate.ai.llm;

import com.syndicate.common.ChecksumUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptTemplateLoader {

    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public PromptTemplate load(String templateName, String version) {
        String key = templateName + ":" + version;
        return templateCache.computeIfAbsent(key, k -> loadFromClasspath(templateName, version));
    }

    private PromptTemplate loadFromClasspath(String templateName, String version) {
        String resourcePath = "classpath:prompts/" + templateName + "/" + version + ".txt";
        Resource resource = resolver.getResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt template not found at: " + resourcePath);
        }

        try (InputStream is = resource.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            String hash = ChecksumUtil.sha256Hex(bytes);
            return new PromptTemplate(templateName, version, content, hash);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template from: " + resourcePath, e);
        }
    }
}
