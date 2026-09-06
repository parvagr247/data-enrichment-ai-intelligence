package com.subdual.ai_intelligent_service.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads, caches, and renders externalized prompt templates from resources/prompts/ (Tasks 74, 75).
 */
@Service
public class PromptTemplateService {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String render(String templateName, Map<String, Object> variables) {
        String raw = loadTemplate(templateName);
        if (variables == null || variables.isEmpty()) {
            return raw;
        }

        String rendered = raw;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "<" + entry.getKey() + ">";
            String val = entry.getValue() != null ? entry.getValue().toString() : "";
            rendered = rendered.replace(placeholder, val);
        }
        return rendered;
    }

    public String loadTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, name -> {
            String path = "classpath:prompts/" + name + (name.endsWith(".st") ? "" : ".st");
            Resource resource = resourceLoader.getResource(path);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to load prompt template from " + path, ex);
            }
        });
    }
}
