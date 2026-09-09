package com.subdual.research_service.integration.persistence.client;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.config.ServiceMeshProperties;
import com.subdual.research_service.integration.persistence.dto.EntityAttributeDto;
import com.subdual.research_service.integration.persistence.dto.EntitySourceDto;
import com.subdual.research_service.integration.persistence.dto.PersistEntityRequest;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RestDatasetPersistenceClient implements DatasetPersistenceClient {

    private final RestClient restClient;
    private final String serviceUrl;

    public RestDatasetPersistenceClient(ServiceMeshProperties properties, RestClient.Builder restClientBuilder) {
        this.serviceUrl = properties != null ? properties.datasetServiceUrl() : "http://localhost:9743";
        this.restClient = createHttpClient(serviceUrl, restClientBuilder);
    }

    @Override // Persists researched entity profile to the central dataset service.
    public void persistEntity( ResearchTarget target, List<ResearchSource> sources, Map<String, EvidenceTuple> attributes ) {
        if (target == null) return;
        
        try {
            PersistEntityRequest request = buildPersistRequest(target, sources, attributes);
            executePersist(request, target);
        } catch (Exception ex) {
            log.warn("[ServiceMesh: PERSISTENCE_SKIPPED] Failed to persist entity '{}' to dataset-service ({}): {}",
                    target.displayName(), serviceUrl, ex.getMessage());
        }
    }

    private PersistEntityRequest buildPersistRequest(
            ResearchTarget target,
            List<ResearchSource> sources,
            Map<String, EvidenceTuple> attributes
    ) {
        String entityType = target.entityType() != null ? target.entityType().name() : "OTHER";
        List<EntitySourceDto> sourceDtos = mapSourceDtos(sources);
        Map<String, EntityAttributeDto> attributeDtos = mapAttributeDtos(attributes);

        return new PersistEntityRequest(
                target.entityId(),
                target.displayName(),
                entityType,
                target.canonicalUrl(),
                sourceDtos,
                attributeDtos
        );
    }

    private List<EntitySourceDto> mapSourceDtos(List<ResearchSource> sources) {
        if (sources == null || sources.isEmpty()) return List.of();
        
        return sources.stream()
                .map(this::toSourceDto)
                .toList();
    }

    private EntitySourceDto toSourceDto(ResearchSource s) {
        return new EntitySourceDto(
                s.url(),
                s.title(),
                s.snippet(),
                s.sourceType(),
                s.domain(),
                s.provider(),
                s.relevance(),
                s.retrievedAt()
        );
    }

    private Map<String, EntityAttributeDto> mapAttributeDtos(Map<String, EvidenceTuple> attributes) {
        Map<String, EntityAttributeDto> attributeDtos = new LinkedHashMap<>();
        if (attributes != null) {
            attributes.forEach((key, tuple) -> {
                if (tuple != null) {
                    attributeDtos.put(key, toAttributeDto(tuple));
                }
            });
        }
        return attributeDtos;
    }

    private EntityAttributeDto toAttributeDto(EvidenceTuple tuple) {
        String confidence = tuple.confidence() != null ? tuple.confidence().name() : "LOW";
        return new EntityAttributeDto(
                tuple.value(),
                tuple.sourceUrl(),
                tuple.evidenceSnippet(),
                confidence
        );
    }

    private void executePersist(PersistEntityRequest request, ResearchTarget target) {
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
    }

    private static RestClient createHttpClient(String serviceUrl, RestClient.Builder restClientBuilder) {
        
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(3000));
        requestFactory.setReadTimeout(Duration.ofMillis(5000));

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        return builder
                .baseUrl(serviceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
