package com.subdual.research_service.extraction.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {

    @Test
    @DisplayName("Should strip honorifics like Dr., Mr., Prof., PhD")
    void shouldStripHonorifics() {
        assertThat(NameNormalizer.normalize("Dr. Jane Doe")).isEqualTo("Jane Doe");
        assertThat(NameNormalizer.normalize("Prof. John Smith, PhD")).isEqualTo("John Smith");
        assertThat(NameNormalizer.normalize("Sir Arthur Conan Doyle")).isEqualTo("Arthur Conan Doyle");
        assertThat(NameNormalizer.normalize("Ms. Alice Cooper")).isEqualTo("Alice Cooper");
    }

    @Test
    @DisplayName("Should strip emojis and normalize irregular whitespace")
    void shouldStripEmojisAndWhitespace() {
        assertThat(NameNormalizer.normalize("  Jane   Doe 🚀✨ ")).isEqualTo("Jane Doe");
        assertThat(NameNormalizer.normalize("John   A.   Doe")).isEqualTo("John A. Doe");
    }

    @Test
    @DisplayName("Should generate standardized comparison key")
    void shouldGenerateComparisonKey() {
        assertThat(NameNormalizer.toComparisonKey("Dr. Jane Doe")).isEqualTo("janedoe");
        assertThat(NameNormalizer.toComparisonKey("Jane Doe 🚀")).isEqualTo("janedoe");
        assertThat(NameNormalizer.toComparisonKey("jane-doe")).isEqualTo("janedoe");
    }

    @Test
    @DisplayName("Should handle null and blank gracefully")
    void shouldHandleNullAndBlank() {
        assertThat(NameNormalizer.normalize(null)).isEmpty();
        assertThat(NameNormalizer.normalize("   ")).isEmpty();
        assertThat(NameNormalizer.toComparisonKey(null)).isEmpty();
    }
}
