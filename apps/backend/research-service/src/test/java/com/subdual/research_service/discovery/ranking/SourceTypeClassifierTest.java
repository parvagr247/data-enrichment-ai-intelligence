package com.subdual.research_service.discovery.ranking;

import com.subdual.research_service.research.model.SourceReliability;
import com.subdual.research_service.research.model.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceTypeClassifierTest {

    @Test
    @DisplayName("Should classify official website when host matches canonical URL")
    void shouldClassifyOfficialWebsite() {
        SourceType type = SourceTypeClassifier.classify(
                "https://spring.io/projects/spring-boot",
                "https://spring.io"
        );
        assertThat(type).isEqualTo(SourceType.OFFICIAL_WEBSITE);
        assertThat(SourceTypeClassifier.determineReliability(type)).isEqualTo(SourceReliability.HIGH);
    }

    @Test
    @DisplayName("Should classify repository, professional profile, and documentation correctly")
    void shouldClassifyKnownDomainTypes() {
        assertThat(SourceTypeClassifier.classify("https://github.com/torvalds/linux", null))
                .isEqualTo(SourceType.REPOSITORY);
        assertThat(SourceTypeClassifier.classify("https://www.linkedin.com/in/johndoe", null))
                .isEqualTo(SourceType.PROFESSIONAL_PROFILE);
        assertThat(SourceTypeClassifier.classify("https://docs.docker.com/engine", null))
                .isEqualTo(SourceType.DOCUMENTATION);
        assertThat(SourceTypeClassifier.classify("https://techcrunch.com/2026/article", null))
                .isEqualTo(SourceType.NEWS);
    }

    @Test
    @DisplayName("Should fallback to SEARCH_RESULT for generic domains")
    void shouldFallbackToSearchResult() {
        SourceType type = SourceTypeClassifier.classify("https://random-tech-blog.org/post", null);
        assertThat(type).isEqualTo(SourceType.SEARCH_RESULT);
        assertThat(SourceTypeClassifier.determineReliability(type)).isEqualTo(SourceReliability.LOW);
    }
}
