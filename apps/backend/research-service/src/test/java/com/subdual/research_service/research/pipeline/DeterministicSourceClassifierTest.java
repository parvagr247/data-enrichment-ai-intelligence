package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicSourceClassifierTest {

    private DeterministicSourceClassifier classifier;
    private ResearchTarget target;

    @BeforeEach
    void setUp() {
        classifier = new DeterministicSourceClassifier();
        target = new ResearchTarget(
                "https://spring.io",
                "https://spring.io/",
                "id-123",
                EntityType.ORGANIZATION,
                "Spring"
        );
    }

    @Test
    @DisplayName("Should classify host match as OFFICIAL_WEBSITE")
    void shouldClassifyHostMatchAsOfficialWebsite() {
        String result = classifier.classify("https://spring.io/projects/spring-boot", null, null, target);
        assertThat(result).isEqualTo("OFFICIAL_WEBSITE");

        String wwwResult = classifier.classify("https://www.spring.io/about", null, null, target);
        assertThat(wwwResult).isEqualTo("OFFICIAL_WEBSITE");
    }

    @Test
    @DisplayName("Should classify documentation URLs as DOCUMENTATION")
    void shouldClassifyDocumentation() {
        assertThat(classifier.classify("https://docs.spring.io/spring-boot/docs", null, null, target))
                .isEqualTo("DOCUMENTATION");
        assertThat(classifier.classify("https://example.com/reference/manual", null, null, target))
                .isEqualTo("DOCUMENTATION");
        assertThat(classifier.classify("https://readthedocs.io/projects/test", null, null, target))
                .isEqualTo("DOCUMENTATION");
    }

    @Test
    @DisplayName("Should classify social platforms as SOCIAL_PROFILE")
    void shouldClassifySocialProfile() {
        assertThat(classifier.classify("https://www.linkedin.com/company/spring", null, null, target))
                .isEqualTo("SOCIAL_PROFILE");
        assertThat(classifier.classify("https://twitter.com/springboot", null, null, target))
                .isEqualTo("SOCIAL_PROFILE");
        assertThat(classifier.classify("https://x.com/springboot", null, null, target))
                .isEqualTo("SOCIAL_PROFILE");
    }

    @Test
    @DisplayName("Should classify major news outlets as NEWS")
    void shouldClassifyNews() {
        assertThat(classifier.classify("https://techcrunch.com/2026/09/spring-update", null, null, target))
                .isEqualTo("NEWS");
        assertThat(classifier.classify("https://www.reuters.com/technology/article", null, null, target))
                .isEqualTo("NEWS");
        assertThat(classifier.classify("https://forbes.com/innovation/tech", null, null, target))
                .isEqualTo("NEWS");
    }

    @Test
    @DisplayName("Should classify code repositories as GITHUB")
    void shouldClassifyRepository() {
        assertThat(classifier.classify("https://github.com/spring-projects/spring-boot", null, null, target))
                .isEqualTo("GITHUB");
        assertThat(classifier.classify("https://gitlab.com/test-org/repo", null, null, target))
                .isEqualTo("GITHUB");
    }

    @Test
    @DisplayName("Should classify general web results as SEARCH_RESULT")
    void shouldClassifySearchResult() {
        assertThat(classifier.classify("https://unrelated-domain.com/some-page", null, null, target))
                .isEqualTo("SEARCH_RESULT");
    }

    @Test
    @DisplayName("Should return UNKNOWN for null or empty URLs")
    void shouldReturnUnknownForNullOrEmpty() {
        assertThat(classifier.classify(null, null, null, target)).isEqualTo("UNKNOWN");
        assertThat(classifier.classify("", null, null, target)).isEqualTo("UNKNOWN");
        assertThat(classifier.classify("   ", null, null, target)).isEqualTo("UNKNOWN");
    }
}
