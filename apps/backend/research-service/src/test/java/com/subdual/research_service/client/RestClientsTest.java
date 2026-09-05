package com.subdual.research_service.client;

import com.subdual.research_service.client.dto.AiExtractedFact;
import com.subdual.research_service.configuration.ServiceMeshProperties;
import com.subdual.research_service.domain.ConfidenceTier;
import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.EvidenceTuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RestClientsTest {

    @Test
    @DisplayName("RestAiExtractionClient should gracefully return empty map when upstream is offline/unreachable")
    void restAiExtractionClientShouldFallbackGracefullyWhenOffline() {
        // Pointing to a dummy port that is definitely closed
        ServiceMeshProperties properties = new ServiceMeshProperties("http://127.0.0.1:59998", "http://127.0.0.1:59999");
        RestAiExtractionClient client = new RestAiExtractionClient(properties);

        Map<String, AiExtractedFact> facts = client.extractFacts(
                "Acme Corp",
                "ORGANIZATION",
                "https://acme.org",
                "Acme Corp is an engineering enterprise.",
                List.of("description", "organization")
        );

        assertThat(facts).isNotNull();
        assertThat(facts).isEmpty();
    }

    @Test
    @DisplayName("RestDatasetPersistenceClient should gracefully swallow network errors when upstream is offline")
    void restDatasetPersistenceClientShouldFallbackGracefullyWhenOffline() {
        ServiceMeshProperties properties = new ServiceMeshProperties("http://127.0.0.1:59998", "http://127.0.0.1:59999");
        RestDatasetPersistenceClient client = new RestDatasetPersistenceClient(properties);

        ResearchTarget target = new ResearchTarget(
                "https://acme.org",
                "https://acme.org",
                "test-id",
                EntityType.ORGANIZATION,
                "Acme Corp"
        );

        ResearchSource source = new ResearchSource(
                "https://acme.org",
                "Acme Corp",
                "OFFICIAL_WEBSITE",
                Instant.now(),
                1.0,
                1.0,
                "Acme snippet"
        );

        Map<String, EvidenceTuple> attributes = Map.of(
                "description", new EvidenceTuple("Acme Corp", "https://acme.org", "Snippet", ConfidenceTier.HIGH)
        );

        // Must not throw any exception
        assertThatCode(() -> client.persistEntity(target, List.of(source), attributes))
                .doesNotThrowAnyException();
    }
}
