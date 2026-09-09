package com.subdual.dataset_service.enrichment.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ResearchServiceClient {

    private final RestClient restClient;
    private final String researchServiceBaseUrl;

    public ResearchServiceClient(
            RestClient.Builder builder,
            @Value("${services.research.url:${RESEARCH_SERVICE_URL:http://research-service:9741}}") String researchServiceBaseUrl
    ) {
        this.restClient = builder.baseUrl(researchServiceBaseUrl).build();
        this.researchServiceBaseUrl = researchServiceBaseUrl;
    }

    public record ResearchCallRequest(
            String url,
            String entityType,
            String name,
            String organization,
            String role,
            List<String> targetFields,
            String userRequirement,
            Map<String, Object> metadata,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String location
    ) {
        public ResearchCallRequest(
                String url,
                String entityType,
                String name,
                String organization,
                String role,
                List<String> targetFields,
                String userRequirement,
                Map<String, Object> metadata
        ) {
            this(url, entityType, name, organization, role, targetFields, userRequirement, metadata, null, null, null, null, null);
        }

        public ResearchCallRequest(
                String url,
                String entityType,
                String name,
                String organization,
                String role,
                List<String> targetFields,
                String userRequirement
        ) {
            this(url, entityType, name, organization, role, targetFields, userRequirement, Map.of(), null, null, null, null, null);
        }
    }

    public record ResearchCallResult(
            String displayName,
            String entityType,
            String canonicalUrl,
            Map<String, EvidenceTupleDto> attributes
    ) {}

    public record EvidenceTupleDto(
            String value,
            String sourceUrl,
            String evidenceSnippet,
            String confidence,
            List<String> corroboratingSources,
            boolean conflictDetected
    ) {}

    public record SourceItemDto(
            String url,
            String title,
            String snippet,
            String sourceType,
            String domain,
            String provider,
            Double relevance,
            String retrievedAt
    ) {}

    public record ResearchCallResponse(
            String status,
            String entityId,
            ResearchCallResult result,
            List<SourceItemDto> sources,
            long executionTimeMs,
            List<String> warnings
    ) {}

    public ResearchCallResponse executeResearch(ResearchCallRequest request) {
        log.info("Dispatching research request to {} for name='{}', url='{}'",
                researchServiceBaseUrl, request.name(), request.url());

        return restClient.post()
                .uri("/api/v1/research")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ResearchCallResponse.class);
    }
}
