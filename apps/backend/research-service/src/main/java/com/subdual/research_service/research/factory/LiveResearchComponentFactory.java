package com.subdual.research_service.research.factory;

import org.springframework.web.client.RestClient;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.ServiceMeshProperties;
import com.subdual.research_service.configuration.WebFetchProperties;
import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.ai.RestAiExtractionClient;
import com.subdual.research_service.integration.dataset.DatasetPersistenceClient;
import com.subdual.research_service.integration.dataset.RestDatasetPersistenceClient;
import com.subdual.research_service.integration.web.CachingWebContentFetcher;
import com.subdual.research_service.integration.web.DefaultWebContentFetcher;
import com.subdual.research_service.integration.web.WebContentFetcher;
import com.subdual.research_service.research.discovery.cache.ThreadSafeSourceCache;
import com.subdual.research_service.research.discovery.provider.MockSearchProvider;
import com.subdual.research_service.research.discovery.provider.ResilientSearchProviderDecorator;
import com.subdual.research_service.research.discovery.provider.SearchProvider;
import com.subdual.research_service.research.discovery.provider.TavilySearchProvider;

/**
 * Concrete factory manufacturing live cloud and service-mesh integrated research components.
 */
public class LiveResearchComponentFactory implements ResearchComponentFactory {

    private final ResearchDiscoveryProperties discoveryProperties;
    private final ServiceMeshProperties serviceMeshProperties;
    private final WebFetchProperties webFetchProperties;
    private final RestClient.Builder restClientBuilder;

    public LiveResearchComponentFactory(
            ResearchDiscoveryProperties discoveryProperties,
            ServiceMeshProperties serviceMeshProperties,
            WebFetchProperties webFetchProperties,
            RestClient.Builder restClientBuilder
    ) {
        this.discoveryProperties = discoveryProperties;
        this.serviceMeshProperties = serviceMeshProperties;
        this.webFetchProperties = webFetchProperties;
        this.restClientBuilder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
    }

    @Override
    public SearchProvider createSearchProvider() {
        SearchProvider primary = new TavilySearchProvider(discoveryProperties);
        SearchProvider fallback = new MockSearchProvider();
        return new ResilientSearchProviderDecorator(primary, fallback);
    }

    @Override
    public AiExtractionClient createAiExtractionClient() {
        return new RestAiExtractionClient(serviceMeshProperties, restClientBuilder);
    }

    @Override
    public DatasetPersistenceClient createDatasetPersistenceClient() {
        return new RestDatasetPersistenceClient(serviceMeshProperties, restClientBuilder);
    }

    @Override
    public WebContentFetcher createWebContentFetcher() {
        return new CachingWebContentFetcher(
                new DefaultWebContentFetcher(webFetchProperties, discoveryProperties),
                ThreadSafeSourceCache.getInstance()
        );
    }
}
