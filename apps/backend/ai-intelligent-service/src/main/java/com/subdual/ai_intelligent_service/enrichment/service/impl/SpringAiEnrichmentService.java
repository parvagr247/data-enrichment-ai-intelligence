package com.subdual.ai_intelligent_service.enrichment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.enrichment.api.dto.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.EnrichedAttributeResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.enrichment.service.EnrichmentAIService;
import com.subdual.ai_intelligent_service.enrichment.service.helper.DeterministicEnrichmentHelper;
import com.subdual.ai_intelligent_service.exception.AiErrorClassifier;
import com.subdual.ai_intelligent_service.normalization.AiOutputNormalizer;
import com.subdual.ai_intelligent_service.prompt.PromptTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SpringAiEnrichmentService implements EnrichmentAIService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final String geminiApiKey;
    private final DeterministicEnrichmentHelper helper;

    public SpringAiEnrichmentService(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey,
            DeterministicEnrichmentHelper helper
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.geminiApiKey = geminiApiKey;
        this.helper = helper;
    }

    @Override // Interprets requirement text into target enrichment fields.
    public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
        String requirement = request != null ? request.requirement() : null;
        String entityType = request != null && request.entityType() != null ? request.entityType().toUpperCase(Locale.ROOT) : "PERSON";

        if (requirement == null || requirement.isBlank()) {
            return helper.buildDefaultScope(entityType);
        }

        if (!isMockMode() && chatModel != null) {
            try {
                RequirementInterpretationResponse response = executeAiInterpretation(requirement, entityType);
                if (response != null) {
                    return response;
                }
            } catch (Exception ex) {
                logAiError("requirement interpretation", ex);
            }
        }

        return helper.interpretDeterministically(requirement, entityType);
    }

    @Override // Cleans and normalizes raw input attributes for downstream enrichment.
    public InputCleansingResponse cleanInput(InputCleansingRequest request) {
        return helper.cleanInput(request);
    }

    @Override // Synthesizes research evidence and input data into enriched attributes.
    public AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request) {
        long startTime = System.currentTimeMillis();

        if (!isMockMode() && chatModel != null) {
            try {
                AIEnrichmentResult result = executeAiSynthesis(request, startTime);
                if (result != null) {
                    return result;
                }
            } catch (Exception ex) {
                logAiError("enrichment synthesis", ex);
            }
        }

        return helper.synthesizeDeterministically(request, startTime);
    }

    private RequirementInterpretationResponse executeAiInterpretation(String requirement, String entityType) {
        String promptText = String.format(PromptTemplates.REQUIREMENT_INTERPRETATION_PROMPT, entityType, requirement.trim());
        String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        return parseRequirementResponse(responseText);
    }

    private AIEnrichmentResult executeAiSynthesis(EnrichmentSynthesisRequest request, long startTime) {
        String promptText = buildSynthesisPromptText(request);
        String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        return parseSynthesisResponse(responseText, request, startTime);
    }

    private boolean isMockMode() {
        return properties.mockMode()
                || geminiApiKey == null
                || geminiApiKey.isBlank()
                || "mock-key".equalsIgnoreCase(geminiApiKey)
                || geminiApiKey.contains("your-");
    }

    private void logAiError(String operation, Exception ex) {
        var c = AiErrorClassifier.classify(ex);
        log.warn("Spring AI {} failed [category='{}', status={}, reason='{}'], falling back to deterministic extraction",
                operation, c.category(), c.httpStatusCode(), c.sanitizedMessage());
    }

    private String buildSynthesisPromptText(EnrichmentSynthesisRequest request) {
        String rawInputJson = toJsonSafe(request.rawInput());
        String targetFieldsJson = toJsonSafe(request.targetFields());
        String evidenceJson = toJsonSafe(request.researchEvidence());

        return String.format(PromptTemplates.ENRICHMENT_SYNTHESIS_PROMPT,
                request.displayName(),
                request.entityType(),
                request.canonicalUrl(),
                rawInputJson,
                request.userRequirement() != null ? request.userRequirement() : "Automatic enrichment",
                targetFieldsJson,
                evidenceJson,
                request.displayName(),
                request.entityType(),
                request.canonicalUrl()
        );
    }

    private String toJsonSafe(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private RequirementInterpretationResponse parseRequirementResponse(String responseText) {
        try {
            String cleaned = helper.cleanJsonBlocks(responseText);
            JsonNode root = MAPPER.readTree(cleaned);
            List<String> fields = parseStringList(root, "requestedFields");
            String scope = root.has("scopeDescription") ? root.get("scopeDescription").asText() : "Interpreted scope";
            return new RequirementInterpretationResponse(fields, scope, false);
        } catch (Exception e) {
            log.warn("Failed to parse requirement interpretation response: {}", e.getMessage());
            return null;
        }
    }

    private AIEnrichmentResult parseSynthesisResponse(String responseText, EnrichmentSynthesisRequest request, long startTime) {
        try {
            String cleaned = helper.cleanJsonBlocks(responseText);
            JsonNode root = MAPPER.readTree(cleaned);

            String displayName = root.has("displayName") ? root.get("displayName").asText() : request.displayName();
            String entityType = root.has("entityType") ? root.get("entityType").asText() : request.entityType();
            String canonicalUrl = root.has("canonicalUrl") ? root.get("canonicalUrl").asText() : request.canonicalUrl();
            double confidence = root.has("overallConfidence") ? root.get("overallConfidence").asDouble() : 0.85;

            Map<String, EnrichedAttributeResult> attributes = parseAttributes(root);
            List<String> unresolved = parseStringList(root, "unresolvedFields");
            List<String> conflicts = parseStringList(root, "conflicts");

            long elapsed = System.currentTimeMillis() - startTime;
            return new AIEnrichmentResult(displayName, entityType, canonicalUrl, attributes, unresolved, conflicts, confidence, properties.model(), elapsed);
        } catch (Exception e) {
            log.warn("Failed to parse AI synthesis response: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, EnrichedAttributeResult> parseAttributes(JsonNode root) {
        Map<String, EnrichedAttributeResult> attributes = new LinkedHashMap<>();
        if (root.has("attributes") && root.get("attributes").isObject()) {
            root.get("attributes").fields().forEachRemaining(entry -> {
                JsonNode node = entry.getValue();
                String field = entry.getKey();
                String val = node.has("value") ? node.get("value").asText() : "";
                String origVal = node.has("originalValue") && !node.get("originalValue").isNull() ? node.get("originalValue").asText() : null;
                String conf = node.has("confidence") ? node.get("confidence").asText() : "MEDIUM";
                String status = node.has("status") ? node.get("status").asText() : "VERIFIED";
                String evidence = node.has("evidence") ? node.get("evidence").asText() : "";
                String notes = node.has("notes") ? node.get("notes").asText() : "";
                List<String> sources = new ArrayList<>();
                if (node.has("sources") && node.get("sources").isArray()) {
                    node.get("sources").forEach(s -> sources.add(s.asText()));
                }
                EnrichedAttributeResult norm = AiOutputNormalizer.normalizeAttribute(
                        new EnrichedAttributeResult(field, val, origVal, conf, status, sources, evidence, notes)
                );
                attributes.put(field, norm);
            });
        }
        return attributes;
    }

    private List<String> parseStringList(JsonNode root, String fieldName) {
        List<String> list = new ArrayList<>();
        if (root.has(fieldName) && root.get(fieldName).isArray()) {
            root.get(fieldName).forEach(n -> list.add(n.asText()));
        }
        return list;
    }
}
