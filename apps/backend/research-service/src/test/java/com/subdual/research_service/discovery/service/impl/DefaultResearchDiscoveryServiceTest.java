package com.subdual.research_service.discovery.service.impl;

import com.subdual.research_service.discovery.service.helper.QueryBuilder;

import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.common.exception.ExternalServiceException;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.discovery.provider.SearchProvider;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultResearchDiscoveryServiceTest {

    @Mock
    private SearchProvider searchProvider;

    @Mock
    private QueryBuilder queryBuilder;

    private ResearchDiscoveryProperties properties;
    private DefaultResearchDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        properties = new ResearchDiscoveryProperties(
                "mock",
                "https://api.tavily.com",
                "test-api-key",
                5,
                5000
        );
        discoveryService = new DefaultResearchDiscoveryService(searchProvider, queryBuilder, properties);
    }

    @Test
    @DisplayName("Should successfully coordinate QueryBuilder and SearchProvider")
    void shouldDiscoverSourcesSuccessfully() {
        ResearchTarget target = new ResearchTarget(
                "https://spring.io",
                "https://spring.io/",
                "id-1",
                EntityType.ORGANIZATION,
                "Spring"
        );

        when(queryBuilder.buildDiscoveryQuery(target)).thenReturn("Spring company official");
        List<DiscoveredSource> mockSources = List.of(
                new DiscoveredSource("https://spring.io", "Spring Official", "OFFICIAL_WEBSITE", Instant.now(), 1.0, "Overview")
        );
        when(searchProvider.search("Spring company official", 5)).thenReturn(mockSources);

        List<DiscoveredSource> results = discoveryService.discoverSources(target);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://spring.io");
        verify(queryBuilder).buildDiscoveryQuery(target);
        verify(searchProvider).search("Spring company official", 5);
    }

    @Test
    @DisplayName("Should propagate BusinessRuleException without wrapping")
    void shouldPropagateBusinessRuleException() {
        ResearchTarget target = new ResearchTarget("https://invalid.com", "https://invalid.com", "id-2", EntityType.OTHER, "Invalid");
        when(queryBuilder.buildDiscoveryQuery(target)).thenReturn("Invalid other");
        when(searchProvider.search("Invalid other", 5)).thenThrow(new BusinessRuleException("Unsupported provider"));

        assertThatThrownBy(() -> discoveryService.discoverSources(target))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unsupported provider");
    }

    @Test
    @DisplayName("Should propagate ExternalServiceException without wrapping")
    void shouldPropagateExternalServiceException() {
        ResearchTarget target = new ResearchTarget("https://fail.com", "https://fail.com", "id-3", EntityType.OTHER, "Fail");
        when(queryBuilder.buildDiscoveryQuery(target)).thenReturn("Fail other");
        when(searchProvider.search("Fail other", 5)).thenThrow(new ExternalServiceException("Upstream 502"));

        assertThatThrownBy(() -> discoveryService.discoverSources(target))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Upstream 502");
    }

    @Test
    @DisplayName("Should wrap unexpected generic exception in ExternalServiceException")
    void shouldWrapUnexpectedException() {
        ResearchTarget target = new ResearchTarget("https://error.com", "https://error.com", "id-4", EntityType.OTHER, "Error");
        when(queryBuilder.buildDiscoveryQuery(target)).thenReturn("Error other");
        when(searchProvider.search("Error other", 5)).thenThrow(new RuntimeException("Socket closed abruptly"));

        assertThatThrownBy(() -> discoveryService.discoverSources(target))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Failed to retrieve research sources from provider")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
