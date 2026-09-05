package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.api.dto.ResearchResponse;
import com.subdual.research_service.api.dto.SourceItem;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchStatus;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchResponseFactoryTest {

    private ResearchResponseFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ResearchResponseFactory();
    }

    @Test
    @DisplayName("Should create COMPLETED response when there are no degraded sources")
    void shouldCreateCompletedResponseWhenNoDegradation() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com",
                "https://example.com",
                "entity-123",
                EntityType.ORGANIZATION,
                "Example Org"
        );

        ResearchSource source = new ResearchSource(
                "https://example.com",
                "Example Homepage",
                "OFFICIAL_WEBSITE",
                Instant.now(),
                0.95,
                0.90,
                "Leading example organization"
        );

        Map<String, EvidenceTuple> attributes = Map.of(
                "organization", new EvidenceTuple("Example Org", "https://example.com", "Leading example", ConfidenceTier.HIGH)
        );

        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        ResearchResponse response = factory.createResponse(
                target,
                List.of(source),
                attributes,
                5,
                120L,
                diagnostics,
                "mock"
        );

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        assertThat(response.entityId()).isEqualTo("entity-123");
        assertThat(response.executionTimeMs()).isEqualTo(120L);
        assertThat(response.result().displayName()).isEqualTo("Example Org");
        assertThat(response.result().attributes()).hasSize(1);
        assertThat(response.sources()).hasSize(1);
        assertThat(response.warnings()).isEmpty();

        assertThat(response.metadata()).containsEntry("provider", "mock");
        assertThat(response.metadata()).containsEntry("totalSourcesDiscovered", 5);
        assertThat(response.metadata()).containsEntry("totalSourcesRanked", 1);
        assertThat(response.metadata()).containsEntry("attributesExtracted", 1);
        assertThat(response.metadata()).containsEntry("warningsCount", 0);
    }

    @Test
    @DisplayName("Should create PARTIAL response when diagnostics report degraded sources")
    void shouldCreatePartialResponseWhenSourcesDegraded() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com",
                "https://example.com",
                "entity-456",
                EntityType.ORGANIZATION,
                "Degraded Company"
        );

        ResearchDiagnostics diagnostics = new ResearchDiagnostics();
        diagnostics.recordSourceSkipped("inaccessible.com", "Connection timed out");

        ResearchResponse response = factory.createResponse(
                target,
                List.of(),
                Map.of(),
                1,
                250L,
                diagnostics,
                "tavily"
        );

        assertThat(response.status()).isEqualTo(ResearchStatus.PARTIAL);
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().get(0)).contains("inaccessible.com");
        assertThat(response.metadata()).containsEntry("warningsCount", 1);
    }

    @Test
    @DisplayName("Should correctly map ResearchSource to SourceItem")
    void shouldMapSourceToSourceItem() {
        Instant now = Instant.now();
        ResearchSource source = new ResearchSource(
                "https://docs.example.com",
                "Example Docs",
                "DOCUMENTATION",
                now,
                0.88,
                0.80,
                "Comprehensive API docs"
        );

        SourceItem item = factory.toSourceItem(source);

        assertThat(item.url()).isEqualTo("https://docs.example.com");
        assertThat(item.title()).isEqualTo("Example Docs");
        assertThat(item.sourceType()).isEqualTo("DOCUMENTATION");
        assertThat(item.snippet()).isEqualTo("Comprehensive API docs");
        assertThat(item.domain()).isEqualTo("docs.example.com");
        assertThat(item.relevance()).isEqualTo(0.88);
        assertThat(item.retrievedAt()).isEqualTo(now);
    }
}
