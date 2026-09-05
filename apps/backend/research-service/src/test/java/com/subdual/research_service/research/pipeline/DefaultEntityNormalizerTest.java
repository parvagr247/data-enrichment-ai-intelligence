package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultEntityNormalizerTest {

    private DefaultEntityNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new DefaultEntityNormalizer();
    }

    @Test
    @DisplayName("Should normalize URL scheme, host, and port correctly")
    void shouldNormalizeUrl() {
        ResearchRequest request = new ResearchRequest(
                "HTTPS://Example.COM:443/products/ai-platform?ref=search",
                EntityType.PRODUCT,
                "  AI   Platform  "
        );

        ResearchTarget target = normalizer.normalize(request);

        assertThat(target.canonicalUrl()).isEqualTo("https://example.com/products/ai-platform?ref=search");
        assertThat(target.displayName()).isEqualTo("AI Platform");
        assertThat(target.entityType()).isEqualTo(EntityType.PRODUCT);
        assertThat(target.entityId()).hasSize(64); // SHA-256 hex string
    }

    @Test
    @DisplayName("Should derive fallback display name from canonical URL when name is missing or whitespace")
    void shouldFallbackDisplayName() {
        ResearchRequest request = new ResearchRequest(
                "https://spring.io/projects/spring-boot",
                null,
                "   "
        );

        ResearchTarget target = normalizer.normalize(request);

        assertThat(target.displayName()).isEqualTo("https://spring.io/projects/spring-boot");
        assertThat(target.entityType()).isEqualTo(EntityType.OTHER);
    }

    @Test
    @DisplayName("Should pass metadata through unmodifiable")
    void shouldPassMetadata() {
        ResearchRequest request = new ResearchRequest(
                "https://example.com",
                EntityType.ORGANIZATION,
                "Example Org",
                Map.of("category", "tech", "priority", 1)
        );

        ResearchTarget target = normalizer.normalize(request);

        assertThat(target.metadata()).containsEntry("category", "tech");
        assertThat(target.metadata()).containsEntry("priority", 1);
    }

    @Test
    @DisplayName("Should reject null request")
    void shouldRejectNull() {
        assertThatThrownBy(() -> normalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
