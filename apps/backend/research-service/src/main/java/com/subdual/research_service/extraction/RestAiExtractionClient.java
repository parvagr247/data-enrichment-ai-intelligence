package com.subdual.research_service.extraction;

import com.subdual.research_service.extraction.dto.AiExtractedFact;
import com.subdual.research_service.extraction.dto.AiExtractionRequest;
import com.subdual.research_service.extraction.dto.AiExtractionResponse;
import com.subdual.research_service.configuration.ServiceMeshProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class RestAiExtractionClient implements AiExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(RestAiExtractionClient.class);

    private final RestClient restClient;
    private final String serviceUrl;

    @Autowired
    public RestAiExtractionClient(ServiceMeshProperties properties, RestClient.Builder restClientBuilder) {
        this.serviceUrl = properties != null ? properties.aiIntelligentServiceUrl() : "http://localhost:9742";
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(3000));
        requestFactory.setReadTimeout(Duration.ofMillis(5000));

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(this.serviceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public RestAiExtractionClient(ServiceMeshProperties properties) {
        this(properties, RestClient.builder());
    }

    public RestAiExtractionClient(String serviceUrl) {
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
    public Map<String, AiExtractedFact> extractFacts(
            String entityName,
            String entityType,
            String sourceUrl,
            String textContent,
            List<String> targetFields
    ) {
        try {
            AiExtractionRequest request = new AiExtractionRequest(
                    entityName,
                    entityType,
                    sourceUrl,
                    textContent,
                    targetFields
            );

            log.info("[ServiceMesh: AI_EXTRACTION] Requesting fact extraction from {} for entity '{}'",
                    serviceUrl, entityName);

            AiExtractionResponse response = restClient.post()
                    .uri("/api/v1/ai/extract")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiExtractionResponse.class);

            if (response != null && response.facts() != null) {
                log.info("[ServiceMesh: AI_EXTRACTION] Successfully extracted {} facts for entity '{}'",
                        response.facts().size(), entityName);
                return response.facts();
            }
        } catch (Exception ex) {
            log.warn("[ServiceMesh: AI_EXTRACTION_SKIPPED] AI extraction request to {} failed: {}",
                    serviceUrl, ex.getMessage());
        }

        return Collections.emptyMap();
    }
}
