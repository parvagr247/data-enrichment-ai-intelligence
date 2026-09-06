package com.subdual.research_service.extraction.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzyMatcherTest {

    @Test
    @DisplayName("Should compute Jaccard token similarity accurately")
    void shouldComputeJaccardSimilarity() {
        double exact = FuzzyMatcher.jaccardTokenSimilarity("John Doe", "John Doe");
        assertThat(exact).isEqualTo(1.0);

        double partial = FuzzyMatcher.jaccardTokenSimilarity("John Robert Doe", "John Doe");
        // tokens: {john, robert, doe} and {john, doe}. intersection: 2, union: 3 => 2/3 ~ 0.666
        assertThat(partial).isCloseTo(0.666, org.assertj.core.data.Offset.offset(0.01));

        double disjoint = FuzzyMatcher.jaccardTokenSimilarity("Jane Smith", "John Doe");
        assertThat(disjoint).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should compute Levenshtein similarity accurately")
    void shouldComputeLevenshteinSimilarity() {
        double exact = FuzzyMatcher.levenshteinSimilarity("Acme", "Acme");
        assertThat(exact).isEqualTo(1.0);

        double oneDiff = FuzzyMatcher.levenshteinSimilarity("Google", "Goggle");
        assertThat(oneDiff).isGreaterThan(0.80);

        double different = FuzzyMatcher.levenshteinSimilarity("Microsoft", "Apple");
        assertThat(different).isLessThan(0.30);
    }

    @Test
    @DisplayName("Should detect fuzzy match based on threshold")
    void shouldDetectFuzzyMatch() {
        assertThat(FuzzyMatcher.isFuzzyMatch("John Doe", "Jon Doe", 0.80)).isTrue();
        assertThat(FuzzyMatcher.isFuzzyMatch("OpenAI Inc", "OpenAI", 0.50)).isTrue();
        assertThat(FuzzyMatcher.isFuzzyMatch("Completely Different", "Something Else", 0.70)).isFalse();
    }
}
