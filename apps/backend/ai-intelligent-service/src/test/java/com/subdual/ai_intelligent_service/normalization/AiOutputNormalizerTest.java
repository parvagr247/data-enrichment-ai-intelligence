package com.subdual.ai_intelligent_service.normalization;

import com.subdual.ai_intelligent_service.enrichment.api.dto.EnrichedAttributeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiOutputNormalizerTest {

    @Test
    @DisplayName("Should strip HTML tags and collapse whitespace")
    void shouldStripHtmlAndCleanWhitespace() {
        String input = "<div><span>Staff</span>   <b>Software Engineer</b><br/></div>";
        assertThat(AiOutputNormalizer.normalizeValue(input)).isEqualTo("Staff Software Engineer");
    }

    @Test
    @DisplayName("Should normalize all unknown variants to UNKNOWN")
    void shouldNormalizeUnknownVariants() {
        assertThat(AiOutputNormalizer.normalizeValue("N/A")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("na")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("Not found")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("Unknown")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("none")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("-")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("--")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("null")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("no information")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue("   ")).isEqualTo("UNKNOWN");
        assertThat(AiOutputNormalizer.normalizeValue(null)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Should deduplicate comma-separated list values while preserving order")
    void shouldDeduplicateListValues() {
        String input = "Java, Spring Boot, Python, Java, Docker, Python";
        assertThat(AiOutputNormalizer.normalizeValue(input))
                .isEqualTo("Java, Spring Boot, Python, Docker");
    }

    @Test
    @DisplayName("Should normalize EnrichedAttributeResult correctly when value is unknown")
    void shouldNormalizeAttributeResultForUnknown() {
        EnrichedAttributeResult attr = new EnrichedAttributeResult(
                "education",
                "Not found",
                null,
                "HIGH",
                "VERIFIED",
                List.of("https://example.com"),
                "<p>Nothing mentioned</p>",
                "<span>Note</span>"
        );

        EnrichedAttributeResult normalized = AiOutputNormalizer.normalizeAttribute(attr);

        assertThat(normalized.value()).isEqualTo("UNKNOWN");
        assertThat(normalized.confidence()).isEqualTo("UNKNOWN");
        assertThat(normalized.status()).isEqualTo("UNRESOLVED");
        assertThat(normalized.evidence()).isEqualTo("Nothing mentioned");
        assertThat(normalized.notes()).isEqualTo("Note");
    }
}
