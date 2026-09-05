package com.subdual.research_service.research;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;
import com.subdual.research_service.api.dto.SourceItem;
import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.common.exception.ExternalServiceException;
import com.subdual.research_service.common.validation.ResearchRequestValidator;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.config.WebFetchProperties;
import com.subdual.research_service.discovery.DefaultResearchDiscoveryService;
import com.subdual.research_service.discovery.QueryBuilder;
import com.subdual.research_service.discovery.ResearchDiscoveryService;
import com.subdual.research_service.discovery.ResearchSourceClient;
import com.subdual.research_service.extraction.ContentExtractor;
import com.subdual.research_service.extraction.DefaultSourceEvidenceService;
import com.subdual.research_service.extraction.EntityResolver;
import com.subdual.research_service.extraction.EvidenceExtractor;
import com.subdual.research_service.extraction.SourceEvidenceService;
import com.subdual.research_service.integration.persistence.DefaultResearchSnapshotPersister;
import com.subdual.research_service.integration.persistence.ResearchSnapshotPersister;
import com.subdual.research_service.integration.web.DefaultWebContentFetcher;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchStatus;
import com.subdual.research_service.research.pipeline.DefaultEntityNormalizer;
import com.subdual.research_service.research.pipeline.EntityNormalizer;
import com.subdual.research_service.research.pipeline.ResearchResponseFactory;
import com.subdual.research_service.research.pipeline.SourceProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchServiceTest {

    @Mock
    private ResearchSourceClient researchSourceClient;

    private ResearchService researchService;

    @BeforeEach
    void setUp() {
        ResearchDiscoveryProperties discoveryProperties = new ResearchDiscoveryProperties("mock", "", "https://api.tavily.com", 5, 4000);
        ResearchPipelineProperties pipelineProperties = new ResearchPipelineProperties(5, 50000);
        WebFetchProperties webProperties = new WebFetchProperties(3000, 5000, 5, null);

        ResearchRequestValidator validator = new ResearchRequestValidator();
        EntityNormalizer entityNormalizer = new DefaultEntityNormalizer();
        ResearchDiscoveryService discoveryService = new DefaultResearchDiscoveryService(
                researchSourceClient, new QueryBuilder(), discoveryProperties);
        SourceProcessor sourceProcessor = new SourceProcessor();
        SourceEvidenceService sourceEvidenceService = new DefaultSourceEvidenceService(
                new DefaultWebContentFetcher(webProperties, true),
                new ContentExtractor(),
                new EntityResolver(),
                new EvidenceExtractor(),
                pipelineProperties
        );
        ResearchSnapshotPersister persister = new DefaultResearchSnapshotPersister();
        ResearchResponseFactory responseFactory = new ResearchResponseFactory();

        researchService = new ResearchOrchestrator(
                validator,
                entityNormalizer,
                discoveryService,
                sourceProcessor,
                sourceEvidenceService,
                persister,
                responseFactory,
                discoveryProperties,
                pipelineProperties
        );
    }

    @Test
    @DisplayName("Should execute research successfully with populated discovered sources")
    void shouldExecuteResearchSuccessfullyWithDiscoveredSources() {
        Instant now = Instant.now();
        List<DiscoveredSource> mockSources = List.of(
                new DiscoveredSource("https://github.com/spring-projects/spring-boot",
                        "spring-projects/spring-boot", "GITHUB", now, 1.00),
                new DiscoveredSource("https://spring.io/projects/spring-boot",
                        "Spring Boot Overview", "OFFICIAL_WEBSITE", now, 0.95),
                new DiscoveredSource("https://docs.spring.io/spring-boot",
                        "Spring Boot Reference", "DOCUMENTATION", now, 0.90)
        );

        when(researchSourceClient.discoverSources(anyString(), anyInt())).thenReturn(mockSources);

        ResearchRequest request = new ResearchRequest(
                "https://github.com/spring-projects/spring-boot",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        ResearchResponse response = researchService.executeResearch(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        assertThat(response.entityId()).hasSize(64);
        assertThat(response.result()).isNotNull();
        assertThat(response.result().displayName()).isEqualTo("Spring Boot");
        assertThat(response.result().entityType()).isEqualTo(EntityType.REPOSITORY);
        assertThat(response.result().canonicalUrl()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(response.sources()).hasSize(3);

        SourceItem firstSource = response.sources().get(0);
        assertThat(firstSource.url()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(firstSource.title()).isEqualTo("spring-projects/spring-boot");
        assertThat(firstSource.sourceType()).isEqualTo("GITHUB");
        assertThat(firstSource.relevance()).isEqualTo(1.00);

        verify(researchSourceClient).discoverSources(anyString(), anyInt());
    }

    @Test
    @DisplayName("Should handle empty discovered sources gracefully")
    void shouldHandleEmptyDiscoveredSourcesGracefully() {
        when(researchSourceClient.discoverSources(anyString(), anyInt())).thenReturn(List.of());

        ResearchRequest request = new ResearchRequest(
                "https://example.com/obscure-item",
                EntityType.OTHER,
                "Obscure Item"
        );

        ResearchResponse response = researchService.executeResearch(request);

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        assertThat(response.sources()).isEmpty();
        assertThat(response.result().attributes()).isEmpty();
    }

    @Test
    @DisplayName("Should deduplicate sources and strip tracking parameters")
    void shouldDeduplicateAndCleanTrackingParameters() {
        Instant now = Instant.now();
        List<DiscoveredSource> mockSources = List.of(
                new DiscoveredSource("https://spring.io/projects/spring-boot?utm_source=twitter&utm_medium=social",
                        "Spring Boot", "OFFICIAL_WEBSITE", now, 0.95),
                new DiscoveredSource("https://spring.io/projects/spring-boot?ref=github",
                        "Spring Boot Duplicate", "OFFICIAL_WEBSITE", now, 0.95),
                new DiscoveredSource("https://docs.spring.io/spring-boot",
                        "Spring Boot Docs", "DOCUMENTATION", now, 0.90)
        );

        when(researchSourceClient.discoverSources(anyString(), anyInt())).thenReturn(mockSources);

        ResearchRequest request = new ResearchRequest("https://spring.io", EntityType.ORGANIZATION, "Spring");
        ResearchResponse response = researchService.executeResearch(request);

        // First duplicate with tracking stripped matches second with tracking stripped, so only 2 unique sources survive
        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0).url()).isEqualTo("https://spring.io/projects/spring-boot");
        assertThat(response.sources().get(1).url()).isEqualTo("https://docs.spring.io/spring-boot");
    }

    @Test
    @DisplayName("Should prune malformed and non-HTTP discovered URLs")
    void shouldPruneMalformedDiscoveredUrls() {
        Instant now = Instant.now();
        List<DiscoveredSource> mockSources = List.of(
                new DiscoveredSource("not-a-valid-url", "Invalid", "OTHER", now, 0.5),
                new DiscoveredSource("javascript:alert(1)", "XSS", "OTHER", now, 0.5),
                new DiscoveredSource(null, "Null URL", "OTHER", now, 0.5),
                new DiscoveredSource("https://example.com/valid", "Valid", "OFFICIAL_WEBSITE", now, 0.90)
        );

        when(researchSourceClient.discoverSources(anyString(), anyInt())).thenReturn(mockSources);

        ResearchRequest request = new ResearchRequest("https://example.com", EntityType.WEBSITE, "Example");
        ResearchResponse response = researchService.executeResearch(request);

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).url()).isEqualTo("https://example.com/valid");
    }

    @Test
    @DisplayName("Should propagate ExternalServiceException when discovery provider fails")
    void shouldPropagateExternalServiceException() {
        when(researchSourceClient.discoverSources(anyString(), anyInt()))
                .thenThrow(new ExternalServiceException("Upstream search provider returned 502 Bad Gateway"));

        ResearchRequest request = new ResearchRequest("https://example.com/fail", EntityType.OTHER, "Fail");

        assertThatThrownBy(() -> researchService.executeResearch(request))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("502 Bad Gateway");
    }

    @Test
    @DisplayName("Should default entityType to OTHER and displayName to canonicalUrl when omitted")
    void shouldHandleDefaultEntityTypeAndNullName() {
        when(researchSourceClient.discoverSources(anyString(), anyInt())).thenReturn(List.of());

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
        when(researchSourceClient.discoverSources(anyString(), anyInt())).thenReturn(List.of());

        ResearchRequest request1 = new ResearchRequest("https://example.com/test", EntityType.ORGANIZATION, "Test Org");
        ResearchRequest request2 = new ResearchRequest("https://example.com/test", EntityType.OTHER, "Different Name");

        ResearchResponse response1 = researchService.executeResearch(request1);
        ResearchResponse response2 = researchService.executeResearch(request2);

        assertThat(response1.entityId()).isEqualTo(response2.entityId());
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when request or URL is invalid")
    void shouldThrowBusinessRuleExceptionForInvalidRequest() {
        assertThatThrownBy(() -> researchService.research(null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Request body must not be null");

        ResearchRequest emptyUrlRequest = new ResearchRequest("  ", EntityType.OTHER, "Test");
        assertThatThrownBy(() -> researchService.research(emptyUrlRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
    }

    @Test
    @DisplayName("Should populate verified attributes with provenance and confidence tier")
    void shouldPopulateVerifiedAttributesWithProvenance() {
        Instant now = Instant.now();
        List<DiscoveredSource> mockSources = List.of(
                new DiscoveredSource("https://github.com/spring-projects/spring-boot",
                        "spring-projects/spring-boot", "GITHUB", now, 1.00,
                        "Spring Boot makes it easy to create stand-alone applications.")
        );

        when(researchSourceClient.discoverSources(anyString(), anyInt())).thenReturn(mockSources);

        ResearchRequest request = new ResearchRequest(
                "https://github.com/spring-projects/spring-boot",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        ResearchResponse response = researchService.executeResearch(request);

        assertThat(response.result().attributes()).isNotEmpty();
        assertThat(response.result().attributes()).containsKey("repository");
        EvidenceTuple repoTuple = response.result().attributes().get("repository");
        assertThat(repoTuple.value()).isEqualTo("spring-projects/spring-boot");
        assertThat(repoTuple.sourceUrl()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(repoTuple.confidence()).isEqualTo(ConfidenceTier.HIGH);
    }
}
