package com.subdual.ai_intelligent_service.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateServiceTest {

    private final PromptTemplateService promptTemplateService = new PromptTemplateService(new DefaultResourceLoader());

    @Test
    @DisplayName("Should load and render externalized prompt template with variable substitution")
    void shouldRenderTemplate() {
        String rendered = promptTemplateService.render("requirement-interpretation", Map.of(
                "entityType", "PERSON",
                "requirement", "Extract role and education"
        ));

        assertThat(rendered).contains("The user wants to enrich entities of type: PERSON");
        assertThat(rendered).contains("Extract role and education");
        assertThat(rendered).doesNotContain("<entityType>");
        assertThat(rendered).doesNotContain("<requirement>");
    }

    @Test
    @DisplayName("Should load field extraction prompt template")
    void shouldLoadFieldExtractionTemplate() {
        String raw = promptTemplateService.loadTemplate("field-extraction");
        assertThat(raw).contains("TARGET FIELDS TO EXTRACT:");
        assertThat(raw).contains("CRITICAL EXTRACTION RULES:");
    }
}
