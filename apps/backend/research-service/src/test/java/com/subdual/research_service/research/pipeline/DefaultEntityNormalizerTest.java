package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.api.ResearchRequest;
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

    @Test
    @DisplayName("Should resolve composite display name from firstName and lastName")
    void shouldResolveCompositeDisplayName() {
        ResearchRequest request = new ResearchRequest(
                null,
                EntityType.PERSON,
                null,
                "MNIT Jaipur",
                "Lead",
                java.util.List.of(),
                com.subdual.research_service.research.model.ResearchDepth.NORMAL,
                Map.of(),
                null,
                "Vardhan",
                "Bhati",
                null,
                "vardhan@example.com",
                "Jaipur, India"
        );

        ResearchTarget target = normalizer.normalize(request);

        assertThat(target.displayName()).isEqualTo("Vardhan Bhati");
        assertThat(target.firstName()).isEqualTo("Vardhan");
        assertThat(target.lastName()).isEqualTo("Bhati");
        assertThat(target.email()).isEqualTo("vardhan@example.com");
        assertThat(target.location()).isEqualTo("Jaipur, India");
        assertThat(target.organization()).isEqualTo("MNIT Jaipur");
        assertThat(target.role()).isEqualTo("Lead");
    }

    @Test
    @DisplayName("Should generate distinct entity IDs for two people with same name in different organizations")
    void shouldDistinguishSameNameEntitiesAcrossDifferentOrgs() {
        ResearchRequest rahulGoogle = new ResearchRequest(
                null,
                EntityType.PERSON,
                "Rahul Sharma",
                "Google",
                "Staff SWE",
                java.util.List.of(),
                com.subdual.research_service.research.model.ResearchDepth.NORMAL,
                Map.of(),
                null
        );

        ResearchRequest rahulMsft = new ResearchRequest(
                null,
                EntityType.PERSON,
                "Rahul Sharma",
                "Microsoft",
                "Principal Architect",
                java.util.List.of(),
                com.subdual.research_service.research.model.ResearchDepth.NORMAL,
                Map.of(),
                null
        );

        ResearchTarget target1 = normalizer.normalize(rahulGoogle);
        ResearchTarget target2 = normalizer.normalize(rahulMsft);

        assertThat(target1.displayName()).isEqualTo("Rahul Sharma");
        assertThat(target2.displayName()).isEqualTo("Rahul Sharma");
        assertThat(target1.canonicalUrl()).isNotEqualTo(target2.canonicalUrl());
        assertThat(target1.entityId()).isNotEqualTo(target2.entityId());
    }
}
