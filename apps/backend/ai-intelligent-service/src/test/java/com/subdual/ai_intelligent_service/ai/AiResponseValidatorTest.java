package com.subdual.ai_intelligent_service.ai;

import com.subdual.ai_intelligent_service.ai.helper.AiResponseValidator;
import com.subdual.ai_intelligent_service.extraction.model.ExtractedFact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseValidatorTest {

    private final AiResponseValidator validator = new AiResponseValidator();

    @Test
    @DisplayName("Should accept valid grounded facts within target field set")
    void shouldAcceptValidGroundedFacts() {
        String sourceText = "Alice is a Principal Architect at Stripe in Seattle.";
        Map<String, ExtractedFact> raw = Map.of(
                "role", new ExtractedFact("Principal Architect", "Alice is a Principal Architect", 0.95),
                "unrequested", new ExtractedFact("Something", "Something", 0.50)
        );

        Map<String, ExtractedFact> validated = validator.validateExtractedFacts(raw, List.of("role"), sourceText);

        assertThat(validated).containsKey("role");
        assertThat(validated).doesNotContainKey("unrequested");
        assertThat(validated.get("role").value()).isEqualTo("Principal Architect");
    }

    @Test
    @DisplayName("Should reject ungrounded facts not present in source text")
    void shouldRejectUngroundedFacts() {
        String sourceText = "Alice works on distributed payments.";
        Map<String, ExtractedFact> raw = Map.of(
                "education", new ExtractedFact("Harvard University", "Harvard University PhD", 0.90)
        );

        Map<String, ExtractedFact> validated = validator.validateExtractedFacts(raw, List.of("education"), sourceText);

        assertThat(validated).isEmpty();
    }

    @Test
    @DisplayName("Should reject empty or undefined values")
    void shouldRejectEmptyValues() {
        String sourceText = "Some source text";
        Map<String, ExtractedFact> raw = Map.of(
                "role", new ExtractedFact("null", "null", 0.50),
                "org", new ExtractedFact("", "", 0.50)
        );

        Map<String, ExtractedFact> validated = validator.validateExtractedFacts(raw, List.of("role", "org"), sourceText);

        assertThat(validated).isEmpty();
    }
}
