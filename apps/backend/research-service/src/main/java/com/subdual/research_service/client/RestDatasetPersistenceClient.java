package com.subdual.research_service.client;

import com.subdual.research_service.client.dto.EntityAttributeDto;
import com.subdual.research_service.client.dto.EntitySourceDto;
import com.subdual.research_service.client.dto.PersistEntityRequest;
import com.subdual.research_service.configuration.ServiceMeshProperties;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.EvidenceTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RestDatasetPersistenceClient implements DatasetPersistenceClient {

    private static final Logger log = LoggerFactory.getLogger(RestDatasetPersistenceClient.class);

    private final RestClient restClient;
    private final String serviceUrl;

    @Autowired
    public RestDatasetPersistenceClient(ServiceMeshProperties properties, RestClient.Builder restClientBuilder) {
        this.serviceUrl = properties != null ? properties.datasetServiceUrl() : "http://localhost:9743";
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(3000));
        requestFactory.setReadTimeout(Duration.ofMillis(5000));

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(this.serviceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public RestDatasetPersistenceClient(ServiceMeshProperties properties) {
        this(properties, RestClient.builder());
    }

    public RestDatasetPersistenceClient(String serviceUrl) {
        this.serviceUrl = serviceUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(3000));
        requestFactory.setReadTimeout(Duration.ofMillis(5000));

        this.restClient = RestClient.builder()
                .baseUrl(this.serviceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void persistEntity(
            ResearchTarget target,
            List<ResearchSource> sources,
            Map<String, EvidenceTuple> attributes
    ) {
        if (target == null) {
            return;
        }

        try {
            List<EntitySourceDto> sourceDtos = (sources != null ? sources : List.<ResearchSource>of()).stream()
                    .map(s -> new EntitySourceDto(
                            s.url(),
                            s.title(),
                            s.snippet(),
                            s.sourceType(),
                            s.domain(),
                            s.provider(),
                            s.relevance(),
                            s.retrievedAt()
                    ))
                    .toList();

            Map<String, EntityAttributeDto> attributeDtos = new LinkedHashMap<>();
            if (attributes != null) {
                attributes.forEach((key, tuple) -> {
                    if (tuple != null) {
                        attributeDtos.put(key, new EntityAttributeDto(
                                tuple.value(),
                                tuple.sourceUrl(),
                                tuple.evidenceSnippet(),
                                tuple.confidence() != null ? tuple.confidence().name() : "LOW"
                        ));
                    }
                });
            }

            PersistEntityRequest request = new PersistEntityRequest(
                    target.entityId(),
                    target.displayName(),
                    target.entityType() != null ? target.entityType().name() : "OTHER",
                    target.canonicalUrl(),
                    sourceDtos,
                    attributeDtos
            );

            log.info("[ServiceMesh: PERSISTENCE] Sending entity '{}' (id: {}) to dataset-service at {}",
                    target.displayName(), target.entityId(), serviceUrl);

            restClient.post()
                    .uri("/api/v1/entities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[ServiceMesh: PERSISTED] Entity '{}' (id: {}) persisted successfully",
                    target.displayName(), target.entityId());

        } catch (Exception ex) {
            log.warn("[ServiceMesh: PERSISTENCE_SKIPPED] Failed to persist entity '{}' to dataset-service ({}): {}",
                    target.displayName(), serviceUrl, ex.getMessage());
        }
    }
}
