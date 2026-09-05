package com.subdual.research_service.service;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.source.SourceProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceProcessorTest {

    private SourceProcessor sourceProcessor;

    @BeforeEach
    void setUp() {
        sourceProcessor = new SourceProcessor();
    }

    @Test
    @DisplayName("Should normalize URLs and strip tracking parameters")
    void shouldNormalizeUrlsAndStripTrackingParams() {
        String rawUrl = "HTTPS://Spring.IO:443/projects/spring-boot/?utm_source=twitter&utm_medium=social&ref=github";
        String normalized = sourceProcessor.normalizeDiscoveredUrl(rawUrl);

        assertThat(normalized).isEqualTo("https://spring.io/projects/spring-boot/");
    }

    @Test
    @DisplayName("Should reject non-HTTP schemes and malformed URLs")
    void shouldRejectInvalidUrls() {
        assertThat(sourceProcessor.normalizeDiscoveredUrl("ftp://example.com/file")).isNull();
        assertThat(sourceProcessor.normalizeDiscoveredUrl("javascript:void(0)")).isNull();
        assertThat(sourceProcessor.normalizeDiscoveredUrl("not a url")).isNull();
        assertThat(sourceProcessor.normalizeDiscoveredUrl("")).isNull();
    }

    @Test
    @DisplayName("Should classify OFFICIAL_WEBSITE only when host matches target canonical host")
    void shouldClassifyOfficialWebsiteOnlyOnHostMatch() {
        ResearchTarget target = new ResearchTarget(
                "https://spring.io",
                "https://spring.io/",
                "id-1",
                EntityType.ORGANIZATION,
                "Spring"
        );

        String officialType = sourceProcessor.classifySourceType("https://spring.io/about", "SEARCH_RESULT", target);
        assertThat(officialType).isEqualTo("OFFICIAL_WEBSITE");

        String nonOfficialType = sourceProcessor.classifySourceType("https://spring-fans.com/about", "OFFICIAL_WEBSITE", target);
        assertThat(nonOfficialType).isEqualTo("SEARCH_RESULT");
    }

    @Test
    @DisplayName("Should preserve provider relevance score in ResearchSource and rank by qualityScore")
    void shouldPreserveProviderRelevanceAndRankByQuality() {
        Instant now = Instant.now();
        ResearchTarget target = new ResearchTarget(
                "https://spring.io",
                "https://spring.io/",
                "id-1",
                EntityType.ORGANIZATION,
                "Spring"
        );

        List<DiscoveredSource> raw = List.of(
                new DiscoveredSource("https://spring.io/blog", "Spring Blog", "BLOG", now, 0.85),
                new DiscoveredSource("https://spring.io/projects", "Spring Projects", "OFFICIAL_WEBSITE", now, 0.90)
        );

        List<ResearchSource> processed = sourceProcessor.processSources(raw, target, 5);

        assertThat(processed).hasSize(2);
        // OFFICIAL_WEBSITE has higher authority (1.00) than BLOG (0.65), so it should rank first
        assertThat(processed.get(0).url()).isEqualTo("https://spring.io/projects");
        assertThat(processed.get(0).relevance()).isEqualTo(0.90);
        assertThat(processed.get(0).qualityScore()).isGreaterThan(processed.get(1).qualityScore());

        assertThat(processed.get(1).url()).isEqualTo("https://spring.io/blog");
        assertThat(processed.get(1).relevance()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("Should handle null target and null entityType safely in calculateRankedScore")
    void shouldHandleNullTargetSafely() {
        ResearchTarget nullTypeTarget = new ResearchTarget(
                "https://github.com/test/repo",
                "https://github.com/test/repo",
                "id-2",
                null,
                "Test"
        );

        double score = sourceProcessor.calculateRankedScore(0.80, "GITHUB", nullTypeTarget);
        assertThat(score).isEqualTo(0.80);

        double scoreNullTarget = sourceProcessor.calculateRankedScore(0.80, "GITHUB", null);
        assertThat(scoreNullTarget).isEqualTo(0.80);
    }
}
