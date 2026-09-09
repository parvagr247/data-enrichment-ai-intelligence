package com.subdual.ai_intelligent_service.ai;

import com.subdual.ai_intelligent_service.ai.helper.AiResponseValidator;
import com.subdual.ai_intelligent_service.ai.impl.DeterministicAiIntelligence;
import com.subdual.ai_intelligent_service.ai.impl.SpringAiIntelligence;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionResponse;
import com.subdual.ai_intelligent_service.prompt.PromptTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiIntelligenceTest {

    private SpringAiIntelligence aiIntelligence;
    private DeterministicAiIntelligence deterministicFallback;

    @BeforeEach
    void setUp() {
        PromptTemplateService promptService = new PromptTemplateService(new DefaultResourceLoader());
        AiResponseValidator validator = new AiResponseValidator();
        deterministicFallback = new DeterministicAiIntelligence();
        AiProperties properties = new AiProperties("gemini-2.5-flash", 0.1, true);

        aiIntelligence = new SpringAiIntelligence(
                Optional.empty(),
                properties,
                promptService,
                validator,
                deterministicFallback,
                "mock-key"
        );
    }

    @Test
    @DisplayName("Should extract facts deterministically in mock mode with execution telemetry")
    void shouldExtractFactsInMockMode() {
        ExtractionRequest request = new ExtractionRequest(
                "Jane Doe",
                "PERSON",
                "https://example.com/jane",
                "Jane Doe is a Principal Engineer at Acme Corp based in Chicago.",
                List.of("role", "organization", "location")
        );

        ExtractionResponse response = aiIntelligence.extractFacts(request);

        assertThat(response.facts()).containsKey("role");
        assertThat(response.facts().get("role").value()).contains("Principal Engineer");
        assertThat(response.facts()).containsKey("organization");
        assertThat(response.facts().get("organization").value()).contains("Acme Corp");

        assertThat(aiIntelligence.getLatestMetrics()).isNotNull();
        assertThat(aiIntelligence.getLatestMetrics().status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should interpret user requirement into structured fields")
    void shouldInterpretRequirement() {
        RequirementInterpretationRequest request = new RequirementInterpretationRequest(
                "Find employer, job title, and where they went to university",
                "PERSON",
                Map.of()
        );

        RequirementInterpretationResponse response = aiIntelligence.interpretRequirement(request);

        assertThat(response.requestedFields()).contains("currentRole", "currentOrganization", "education");
    }
}
