package com.subdual.research_service.research.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.ServiceMeshProperties;
import com.subdual.research_service.configuration.WebFetchProperties;

/**
 * Provider coordinating the selection and creation of concrete ResearchComponentFactory instances.
 */
@Component
public class ResearchComponentFactoryProvider {

    private final ResearchDiscoveryProperties discoveryProperties;
    private final ServiceMeshProperties serviceMeshProperties;
    private final WebFetchProperties webFetchProperties;
    private final RestClient.Builder restClientBuilder;

    @Autowired
    public ResearchComponentFactoryProvider(
            @Autowired(required = false) ResearchDiscoveryProperties discoveryProperties,
            @Autowired(required = false) ServiceMeshProperties serviceMeshProperties,
            @Autowired(required = false) WebFetchProperties webFetchProperties,
            @Autowired(required = false) RestClient.Builder restClientBuilder
    ) {
        this.discoveryProperties = discoveryProperties;
        this.serviceMeshProperties = serviceMeshProperties;
        this.webFetchProperties = webFetchProperties;
        this.restClientBuilder = restClientBuilder;
    }

    public ResearchComponentFactory getFactory(boolean offline) {
        if (offline) {
            return new OfflineResearchComponentFactory();
        }
        return new LiveResearchComponentFactory(discoveryProperties, serviceMeshProperties, webFetchProperties, restClientBuilder);
    }

    public ResearchComponentFactory getFactory() {
        boolean isMock = discoveryProperties == null || "mock".equalsIgnoreCase(discoveryProperties.provider());
        return getFactory(isMock);
    }
}
