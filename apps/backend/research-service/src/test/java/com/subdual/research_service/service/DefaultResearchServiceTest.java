package com.subdual.research_service.service;

import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchStatus;
import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultResearchServiceTest {

    private DefaultResearchService researchService;

    @BeforeEach
    void setUp() {
        researchService = new DefaultResearchService();
    }

    @Test
    @DisplayName("Should create research execution context with deterministic entityId and truthful empty attributes")
    void shouldExecuteResearchSuccessfully() {
        ResearchRequest request = new ResearchRequest(
                "https://example.com/profiles/jane-doe",
                EntityType.PERSON,
                "Jane Doe"
        );

        ResearchResponse response = researchService.executeResearch(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        assertThat(response.entityId()).isNotBlank();
        // SHA-256 is 64 hex characters
        assertThat(response.entityId()).hasSize(64);
        assertThat(response.result()).isNotNull();
        assertThat(response.result().displayName()).isEqualTo("Jane Doe");
        assertThat(response.result().entityType()).isEqualTo(EntityType.PERSON);
        assertThat(response.result().canonicalUrl()).isEqualTo("https://example.com/profiles/jane-doe");
        // Must never return fake attributes or fake sources in milestone 1
        assertThat(response.result().attributes()).isEmpty();
        assertThat(response.sources()).isEmpty();
        assertThat(response.executionTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should default entityType to OTHER and displayName to canonicalUrl when omitted")
    void shouldHandleDefaultEntityTypeAndNullName() {
        ResearchRequest request = new ResearchRequest(
                "https://example.com/company",
                null,
                null
        );

        ResearchResponse response = researchService.executeResearch(request);

        assertThat(response.result().entityType()).isEqualTo(EntityType.OTHER);
        assertThat(response.result().displayName()).isEqualTo("https://example.com/company");
        assertThat(response.result().attributes()).isEmpty();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    @DisplayName("Should produce consistent deterministic entityId for same canonical URL")
    void shouldProduceDeterministicEntityId() {
        ResearchRequest request1 = new ResearchRequest("https://example.com/test", EntityType.ORGANIZATION, "Test Org");
        ResearchRequest request2 = new ResearchRequest("https://example.com/test", EntityType.OTHER, "Different Name");

        ResearchResponse response1 = researchService.executeResearch(request1);
        ResearchResponse response2 = researchService.executeResearch(request2);

        assertThat(response1.entityId()).isEqualTo(response2.entityId());
    }
}
