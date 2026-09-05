package com.subdual.research_service.extraction.support;

import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntityResolverTest {

    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EntityResolver();
    }

    @Test
    @DisplayName("Should resolve HIGH confidence on exact host match")
    void shouldResolveHighOnHostMatch() {
        ResearchTarget target = new ResearchTarget(
                "https://spring.io",
                "https://spring.io/",
                "id-1",
                EntityType.ORGANIZATION,
                "Spring Framework"
        );
        ExtractedDocument doc = new ExtractedDocument(
                "https://spring.io/projects/spring-boot",
                "Spring Boot Overview",
                "Spring Boot description",
                "Spring",
                "Spring text",
                Instant.now()
        );

        EntityResolver.ResolutionResult result = resolver.resolve(target, doc);

        assertThat(result.confidence()).isEqualTo(ConfidenceTier.HIGH);
        assertThat(result.matched()).isTrue();
        assertThat(result.reason()).contains("Host match");
    }

    @Test
    @DisplayName("Should resolve HIGH confidence when entity name is matched in page title")
    void shouldResolveHighOnTitleMatch() {
        ResearchTarget target = new ResearchTarget(
                "https://github.com/spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "id-2",
                EntityType.REPOSITORY,
                "Spring Boot"
        );
        ExtractedDocument doc = new ExtractedDocument(
                "https://external-blog.com/spring-boot-guide",
                "A Complete Guide to Spring Boot",
                null,
                null,
                "Spring framework tutorial",
                Instant.now()
        );

        EntityResolver.ResolutionResult result = resolver.resolve(target, doc);

        assertThat(result.confidence()).isEqualTo(ConfidenceTier.HIGH);
        assertThat(result.matched()).isTrue();
        assertThat(result.reason()).contains("title");
    }

    @Test
    @DisplayName("Should resolve MEDIUM confidence when entity name is only mentioned in body")
    void shouldResolveMediumOnBodyMatch() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/company",
                "https://example.com/company",
                "id-3",
                EntityType.ORGANIZATION,
                "Acme Corp"
        );
        ExtractedDocument doc = new ExtractedDocument(
                "https://tech-news.org/article-123",
                "Quarterly Industry Report",
                null,
                null,
                "Several companies including Acme Corp performed well this quarter.",
                Instant.now()
        );

        EntityResolver.ResolutionResult result = resolver.resolve(target, doc);

        assertThat(result.confidence()).isEqualTo(ConfidenceTier.MEDIUM);
        assertThat(result.matched()).isTrue();
        assertThat(result.reason()).contains("body");
    }

    @Test
    @DisplayName("Should resolve LOW confidence and matched false for unrelated document")
    void shouldResolveLowOnUnrelatedDocument() {
        ResearchTarget target = new ResearchTarget(
                "https://openai.com",
                "https://openai.com/",
                "id-4",
                EntityType.ORGANIZATION,
                "OpenAI"
        );
        ExtractedDocument doc = new ExtractedDocument(
                "https://unrelated-domain.com/recipe",
                "Grandma's Apple Pie Recipe",
                null,
                null,
                "Flour, sugar, cinnamon, and apples baked to perfection.",
                Instant.now()
        );

        EntityResolver.ResolutionResult result = resolver.resolve(target, doc);

        assertThat(result.confidence()).isEqualTo(ConfidenceTier.LOW);
        assertThat(result.matched()).isFalse();
    }
}
