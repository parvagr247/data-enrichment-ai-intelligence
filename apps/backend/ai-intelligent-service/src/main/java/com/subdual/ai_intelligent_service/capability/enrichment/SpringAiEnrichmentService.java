package com.subdual.ai_intelligent_service.capability.enrichment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.ai.AiPromptExecutor;
import com.subdual.ai_intelligent_service.ai.PromptTemplates;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.EnrichedAttributeResult;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.capability.enrichment.dto.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.shared.normalization.AiOutputNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Clean, minimalistic implementation of EnrichmentAIService delegating prompt execution
 * to AiPromptExecutor and fallback to DeterministicEnrichmentHelper.
 */
@Service
@Slf4j
public class SpringAiEnrichmentService implements EnrichmentAIService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiPromptExecutor promptExecutor;
    private final DeterministicEnrichmentHelper helper;

    @Autowired
    public SpringAiEnrichmentService(AiPromptExecutor promptExecutor, DeterministicEnrichmentHelper helper) {
        this.promptExecutor = promptExecutor;
        this.helper = helper;
    }

    public SpringAiEnrichmentService(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            String apiKey,
            DeterministicEnrichmentHelper helper
    ) {
        this(new AiPromptExecutor(chatModel, properties, apiKey), helper);
    }

    @Override
    public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
        String requirement = request != null ? request.requirement() : null;
        String entityType = request != null && request.entityType() != null ? request.entityType().toUpperCase(Locale.ROOT) : "PERSON";

        if (requirement == null || requirement.isBlank()) {
            return helper.buildDefaultScope(entityType);
        }

        String promptText = String.format(PromptTemplates.REQUIREMENT_INTERPRETATION_PROMPT, entityType, requirement.trim());
        Optional<String> aiResponse = promptExecutor.executePrompt("REQUIREMENT_INTERPRETATION", promptText, entityType);

        if (aiResponse.isPresent()) {
            RequirementInterpretationResponse parsed = parseRequirementResponse(aiResponse.get());
            if (parsed != null) {
                return parsed;
            }
        }

        return helper.interpretDeterministically(requirement, entityType);
    }

    @Override
    public InputCleansingResponse cleanInput(InputCleansingRequest request) {
        return helper.cleanInput(request);
    }

    @Override
    public AIEnrichmentResult synthesizeEnrichment(EnrichmentSynthesisRequest request) {
        long startTime = System.currentTimeMillis();

        if (request != null) {
            String promptText = buildSynthesisPromptText(request);
            Optional<String> aiResponse = promptExecutor.executePrompt("ENRICHMENT_SYNTHESIS", promptText, request.displayName());

            if (aiResponse.isPresent()) {
                AIEnrichmentResult result = parseSynthesisResponse(aiResponse.get(), request, startTime);
                if (result != null) {
                    return result;
                }
            }
        }

        return helper.synthesizeDeterministically(request, startTime);
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
            JsonNode root = MAPPER.readTree(responseText);
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
            JsonNode root = MAPPER.readTree(responseText);

            String displayName = root.has("displayName") ? root.get("displayName").asText() : request.displayName();
            String entityType = root.has("entityType") ? root.get("entityType").asText() : request.entityType();
            String canonicalUrl = root.has("canonicalUrl") ? root.get("canonicalUrl").asText() : request.canonicalUrl();
            double confidence = root.has("overallConfidence") ? root.get("overallConfidence").asDouble() : 0.85;

            Map<String, EnrichedAttributeResult> attributes = parseAttributes(root);
            List<String> unresolved = parseStringList(root, "unresolvedFields");
            List<String> conflicts = parseStringList(root, "conflicts");

            long elapsed = System.currentTimeMillis() - startTime;
            return new AIEnrichmentResult(displayName, entityType, canonicalUrl, attributes, unresolved, conflicts, confidence, promptExecutor.getModelName(), elapsed);
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
