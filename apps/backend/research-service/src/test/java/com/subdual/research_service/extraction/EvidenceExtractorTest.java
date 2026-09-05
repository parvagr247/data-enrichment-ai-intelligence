package com.subdual.research_service.extraction;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceExtractorTest {

    private EvidenceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EvidenceExtractor();
    }

    @Test
    @DisplayName("Should extract description, title, and repository with provenance for matched document")
    void shouldExtractEvidenceWithProvenance() {
        ResearchTarget target = new ResearchTarget(
                "https://github.com/spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "id-1",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        ResearchSource source = new ResearchSource(
                "https://github.com/spring-projects/spring-boot",
                "spring-projects/spring-boot",
                "GITHUB",
                Instant.now(),
                1.00,
                1.00,
                "Spring Boot repository"
        );

        ExtractedDocument doc = new ExtractedDocument(
                "https://github.com/spring-projects/spring-boot",
                "spring-projects/spring-boot: Spring Boot",
                "Spring Boot makes it easy to create stand-alone applications.",
                "GitHub",
                "Spring Boot is open source software.",
                Instant.now()
        );

        Map<String, EntityResolver.ResolutionResult> resolutions = Map.of(
                doc.url(), new EntityResolver.ResolutionResult(ConfidenceTier.HIGH, true, "Host match")
        );

        Map<String, EvidenceTuple> evidence = extractor.extractEvidence(target, List.of(source), List.of(doc), resolutions);

        assertThat(evidence).containsKey("description");
        EvidenceTuple desc = evidence.get("description");
        assertThat(desc.value()).isEqualTo("Spring Boot makes it easy to create stand-alone applications.");
        assertThat(desc.sourceUrl()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(desc.confidence()).isEqualTo(ConfidenceTier.HIGH);

        assertThat(evidence).containsKey("repository");
        EvidenceTuple repo = evidence.get("repository");
        assertThat(repo.value()).isEqualTo("spring-projects/spring-boot");
        assertThat(repo.sourceUrl()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(repo.confidence()).isEqualTo(ConfidenceTier.HIGH);
    }

    @Test
    @DisplayName("Should NOT extract description from unmatched document (zero hallucination / spam prevention)")
    void shouldNotExtractDescriptionFromUnmatchedDocument() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/company",
                "https://example.com/company",
                "id-2",
                EntityType.ORGANIZATION,
                "Acme Corp"
        );

        ExtractedDocument unrelatedDoc = new ExtractedDocument(
                "https://unrelated-spam.com/page",
                "Spam Article",
                "Cheap loans and discount sunglasses available now.",
                "SpamSite",
                "Spam body content.",
                Instant.now()
        );

        Map<String, EntityResolver.ResolutionResult> resolutions = Map.of(
                unrelatedDoc.url(), new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unrelated")
        );

        Map<String, EvidenceTuple> evidence = extractor.extractEvidence(target, List.of(), List.of(unrelatedDoc), resolutions);

        assertThat(evidence).doesNotContainKey("description");
        assertThat(evidence).doesNotContainKey("title");
    }

    @Test
    @DisplayName("Should fallback to search snippet when full page text is unavailable")
    void shouldFallbackToSearchSnippet() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/item",
                "https://example.com/item",
                "id-3",
                EntityType.PRODUCT,
                "Acme Widget"
        );

        ResearchSource source = new ResearchSource(
                "https://example.com/item",
                "Acme Widget Official",
                "OFFICIAL_WEBSITE",
                Instant.now(),
                0.90,
                0.90,
                "The Acme Widget is a high-performance automation device."
        );

        // Document exists but has empty body and no meta description (e.g. JavaScript-heavy page)
        ExtractedDocument emptyDoc = new ExtractedDocument(
                "https://example.com/item",
                "Acme Widget",
                null,
                null,
                "",
                Instant.now()
        );

        Map<String, EntityResolver.ResolutionResult> resolutions = Map.of(
                emptyDoc.url(), new EntityResolver.ResolutionResult(ConfidenceTier.HIGH, true, "Host match")
        );

        Map<String, EvidenceTuple> evidence = extractor.extractEvidence(target, List.of(source), List.of(emptyDoc), resolutions);

        assertThat(evidence).containsKey("description");
        EvidenceTuple desc = evidence.get("description");
        assertThat(desc.value()).isEqualTo("The Acme Widget is a high-performance automation device.");
        assertThat(desc.sourceUrl()).isEqualTo("https://example.com/item");
    }
}
