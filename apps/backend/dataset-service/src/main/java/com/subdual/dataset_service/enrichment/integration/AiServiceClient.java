package com.subdual.dataset_service.enrichment.integration;

import com.subdual.dataset_service.enrichment.api.dto.request.ProfileAssessmentRequest;
import com.subdual.dataset_service.enrichment.api.dto.response.ProfileAssessmentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(
            RestClient.Builder builder,
            @Value("${services.ai.url:${AI_INTELLIGENT_SERVICE_URL:http://ai-intelligent-service:9742}}") String aiServiceBaseUrl
    ) {
        this.restClient = builder.baseUrl(aiServiceBaseUrl).build();
    }

    public record RequirementCallRequest(String requirement, String entityType, Map<String, String> rawInput) {}
    public record RequirementCallResponse(List<String> requestedFields, String scopeDescription, boolean isDefaultScope) {}

    public record InputCleanCallRequest(Map<String, String> rawInput, String entityType) {}
    public record InputCleanCallResponse(Map<String, String> cleanedInput, Map<String, String> normalizedFields, List<String> notes) {}

    public record FactEvidenceCallDto(
            String field,
            String value,
            String sourceUrl,
            String evidenceSnippet,
            String confidence,
            List<String> corroboratingSources,
            boolean conflictDetected
    ) {}

    public record SynthesisCallRequest(
            Map<String, String> rawInput,
            String displayName,
            String entityType,
            String canonicalUrl,
            String userRequirement,
            List<String> targetFields,
            Map<String, FactEvidenceCallDto> researchEvidence,
            List<String> researchSources
    ) {}

    public record AttributeResultCallDto(
            String field,
            String value,
            String originalValue,
            String confidence,
            String status,
            List<String> sources,
            String evidence,
            String notes
    ) {}

    public record SynthesisCallResponse(
            String displayName,
            String entityType,
            String canonicalUrl,
            Map<String, AttributeResultCallDto> attributes,
            List<String> unresolvedFields,
            List<String> conflicts,
            double overallConfidence,
            String modelUsed,
            long executionTimeMs
    ) {}

    public RequirementCallResponse interpretRequirement(String requirement, String entityType, Map<String, String> rawInput) {
        try {
            return restClient.post()
                    .uri("/api/v1/ai/requirement")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new RequirementCallRequest(requirement, entityType, rawInput))
                    .retrieve()
                    .body(RequirementCallResponse.class);
        } catch (Exception ex) {
            log.warn("Failed calling AI requirement interpretation ({}), using fallback scope", ex.getMessage());
            List<String> defaultFields = "ORGANIZATION".equalsIgnoreCase(entityType)
                    ? List.of("description", "industry", "headquarters", "products")
                    : List.of("currentOrganization", "currentRole", "education", "skills", "location");
            return new RequirementCallResponse(defaultFields, "Fallback requirement scope", true);
        }
    }

    public InputCleanCallResponse cleanInput(Map<String, String> rawInput, String entityType) {
        try {
            return restClient.post()
                    .uri("/api/v1/ai/clean")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new InputCleanCallRequest(rawInput, entityType))
                    .retrieve()
                    .body(InputCleanCallResponse.class);
        } catch (Exception ex) {
            log.warn("Failed calling AI clean input ({}), using raw input as-is", ex.getMessage());
            return new InputCleanCallResponse(rawInput, Map.of(), List.of());
        }
    }

    public SynthesisCallResponse synthesizeEnrichment(SynthesisCallRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/ai/enrich")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SynthesisCallResponse.class);
        } catch (Exception ex) {
            log.warn("Failed calling AI synthesis ({}), using deterministic fallback", ex.getMessage());
            return null;
        }
    }

    public ProfileAssessmentResponse assessProfile(ProfileAssessmentRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v2/ai/profile/assess")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ProfileAssessmentResponse.class);
        } catch (Exception ex) {
            log.warn("Failed calling AI profile assessment ({}), falling back to deterministic synthesis", ex.getMessage());
            return null;
        }
    }
}
