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
        long startTime = System.currentTimeMillis();
        try {
            log.info("[AI_EXTRACTION_REQUEST] upstream='{}' entity='{}' source='{}' fields={}",
                    serviceUrl, entityName, request.sourceUrl(), request.targetFields());

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
                        String safeError = sanitizeErrorBody(errorBody);
                        log.warn("[AI_EXTRACTION_HTTP_ERROR] upstream='{}' status={} contentType='{}' body='{}'",
                                serviceUrl, resp.getStatusCode(), ct, safeError);
                        throw new org.springframework.web.client.RestClientResponseException(
                                "AI service error: " + resp.getStatusCode() + " - " + safeError,
                                resp.getStatusCode().value(),
                                resp.getStatusText(),
                                resp.getHeaders(),
                                errorBody.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                java.nio.charset.StandardCharsets.UTF_8
                        );
                    })
                    .body(AiExtractionResponse.class);

            long durationMs = System.currentTimeMillis() - startTime;
            if (response != null && response.facts() != null) {
                log.info("[AI_EXTRACTION] entity='{}' source='{}' status='SUCCESS' factsExtracted={} model='{}' durationMs={}",
                        entityName, request.sourceUrl(), response.facts().size(),
                        response.modelUsed() != null ? response.modelUsed() : "unknown", durationMs);
                return response.facts();
            }
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.warn("[AI_EXTRACTION] entity='{}' source='{}' status='SKIPPED' reason='{}' durationMs={}",
                    entityName, request.sourceUrl(), ex.getMessage(), durationMs);
        }

        return Collections.emptyMap();
    }

    private static String sanitizeErrorBody(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.replaceAll("(?i)(?:key|token|password|secret|api[_-]?key)[=:\\s]+([A-Za-z0-9_.-]{8,})", "$1=[REDACTED]")
                  .replaceAll("(?i)(?:AIza|AQ\\.)[A-Za-z0-9_-]{15,}", "[REDACTED_API_KEY]");
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
