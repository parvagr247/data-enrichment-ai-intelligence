package com.subdual.research_service.extraction.support;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.research.model.ConfidenceTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceMergerTest {

    private EvidenceMerger merger;

    @BeforeEach
    void setUp() {
        merger = new EvidenceMerger();
    }

    @Test
    @DisplayName("Should boost confidence and accumulate corroborating sources on agreement")
    void shouldBoostConfidenceOnCorroboration() {
        Map<String, EvidenceTuple> attrs = new LinkedHashMap<>();

        merger.mergeAttribute(attrs, "role", "Software Engineer", "https://site-a.com/profile", "John is a Software Engineer", ConfidenceTier.MEDIUM);
        merger.mergeAttribute(attrs, "role", "Software Engineer", "https://site-b.com/profile", "John works as Software Engineer", ConfidenceTier.MEDIUM);

        EvidenceTuple role = attrs.get("role");
        assertThat(role).isNotNull();
        assertThat(role.confidence()).isEqualTo(ConfidenceTier.HIGH);
        assertThat(role.corroboratingSources()).contains("https://site-a.com/profile", "https://site-b.com/profile");
        assertThat(role.conflictDetected()).isFalse();
    }

    @Test
    @DisplayName("Should detect conflict and retain conflict description when values contradict")
    void shouldDetectConflictOnDisagreement() {
        Map<String, EvidenceTuple> attrs = new LinkedHashMap<>();

        merger.mergeAttribute(attrs, "role", "Software Engineer", "https://site-a.com/profile", "Software Engineer at Acme", ConfidenceTier.HIGH);
        merger.mergeAttribute(attrs, "role", "Real Estate Agent", "https://site-c.com/profile", "Licensed Real Estate Agent", ConfidenceTier.HIGH);

        EvidenceTuple role = attrs.get("role");
        assertThat(role).isNotNull();
        assertThat(role.conflictDetected()).isTrue();
        assertThat(role.conflictDescription()).contains("Conflict detected between");
        assertThat(role.conflictDescription()).contains("Software Engineer").contains("Real Estate Agent");
    }

    @Test
    @DisplayName("Should cap search snippet evidence confidence at MEDIUM even if HIGH tier provided")
    void shouldCapSearchSnippetEvidenceAtMediumConfidence() {
        Map<String, EvidenceTuple> attrs = new LinkedHashMap<>();

        merger.mergeAttribute(attrs, "role", "Software Engineer", "https://site-snippet.com", "John is a Software Engineer", ConfidenceTier.HIGH, "SEARCH_ENGINE", "SEARCH_SNIPPET");

        EvidenceTuple role = attrs.get("role");
        assertThat(role).isNotNull();
        assertThat(role.confidence()).isEqualTo(ConfidenceTier.MEDIUM);
        assertThat(role.extractionMethod()).isEqualTo("SEARCH_SNIPPET");
    }

    @Test
    @DisplayName("Should prioritize DIRECT_SOURCE over SEARCH_SNIPPET when evidence values conflict")
    void shouldPrioritizeDirectSourceOverSearchSnippet() {
        Map<String, EvidenceTuple> attrs = new LinkedHashMap<>();

        // Snippet claims Real Estate Agent first
        merger.mergeAttribute(attrs, "role", "Real Estate Agent", "https://snippet-source.com", "Real Estate Agent", ConfidenceTier.MEDIUM, "SEARCH_ENGINE", "SEARCH_SNIPPET");

        // Direct webpage claims Principal Engineer
        merger.mergeAttribute(attrs, "role", "Principal Engineer", "https://personal-blog.com/about", "Principal Engineer at Google", ConfidenceTier.HIGH, "DIRECT_PAGE", "DIRECT_SOURCE");

        EvidenceTuple role = attrs.get("role");
        assertThat(role).isNotNull();
        assertThat(role.value()).isEqualTo("Principal Engineer");
        assertThat(role.sourceUrl()).isEqualTo("https://personal-blog.com/about");
        assertThat(role.conflictDetected()).isTrue();
    }
}
