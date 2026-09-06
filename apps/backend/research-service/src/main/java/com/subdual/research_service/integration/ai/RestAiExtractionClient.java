package com.subdual.research_service.integration.ai;

import com.subdual.research_service.config.ServiceMeshProperties;
import com.subdual.research_service.integration.ai.dto.AiExtractedFact;
import com.subdual.research_service.integration.ai.dto.AiExtractionRequest;
import com.subdual.research_service.integration.ai.dto.AiExtractionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RestAiExtractionClient implements AiExtractionClient {

    private final RestClient restClient;
    private final String serviceUrl;

    public RestAiExtractionClient(ServiceMeshProperties properties, RestClient.Builder restClientBuilder) {
        this.serviceUrl = properties != null ? properties.aiIntelligentServiceUrl() : "http://localhost:9742";
        this.restClient = createHttpClient(this.serviceUrl, restClientBuilder);
    }

    @Override
    public Map<String, AiExtractedFact> extractFacts(
            String entityName,
            String entityType,
            String sourceUrl,
            String textContent,
            List<String> targetFields
    ) {
        if (textContent == null || textContent.isBlank()) {
            return Collections.emptyMap();
        }

        AiExtractionRequest request = new AiExtractionRequest(
                entityName,
                entityType,
                sourceUrl,
                textContent,
                targetFields
        );

        return executeExtraction(request, entityName);
    }

    private Map<String, AiExtractedFact> executeExtraction(AiExtractionRequest request, String entityName) {
        try {
            log.info("[ServiceMesh: AI_EXTRACTION] Requesting fact extraction from {} for entity '{}'",
                    serviceUrl, entityName);

            AiExtractionResponse response = restClient.post()
                    .uri("/api/v1/ai/extract")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("application/problem+json"))
                    .body(request)
                    .retrieve()
                    .onStatus(org.springframework.http.HttpStatusCode::isError, (req, resp) -> {
                        String errorBody = "";
                        try {
                            errorBody = new String(resp.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        } catch (Exception ignored) {}
                        MediaType ct = resp.getHeaders().getContentType();
                        log.warn("[ServiceMesh: AI_HTTP_ERROR] Upstream {} returned status={} contentType={} body={}",
                                serviceUrl, resp.getStatusCode(), ct, errorBody);
                        throw new org.springframework.web.client.RestClientResponseException(
                                "AI service error: " + resp.getStatusCode() + " - " + errorBody,
                                resp.getStatusCode().value(),
                                resp.getStatusText(),
                                resp.getHeaders(),
                                errorBody.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                java.nio.charset.StandardCharsets.UTF_8
                        );
                    })
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

    private static RestClient createHttpClient(String serviceUrl, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(5000));
        requestFactory.setReadTimeout(Duration.ofMillis(30000));

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        return builder
                .baseUrl(serviceUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    for (var converter : converters) {
                        if (converter instanceof org.springframework.http.converter.AbstractHttpMessageConverter<?> ac) {
                            List<MediaType> types = new ArrayList<>(ac.getSupportedMediaTypes());
                            if (types.contains(MediaType.APPLICATION_JSON)) {
                                if (!types.contains(MediaType.valueOf("application/problem+json"))) {
                                    types.add(MediaType.valueOf("application/problem+json"));
                                }
                                if (!types.contains(MediaType.APPLICATION_OCTET_STREAM)) {
                                    types.add(MediaType.APPLICATION_OCTET_STREAM);
                                }
                                ac.setSupportedMediaTypes(types);
                            }
                        }
                    }
                })
                .build();
    }
}
