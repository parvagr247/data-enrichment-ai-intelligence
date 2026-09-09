package com.subdual.ai_intelligent_service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.ai.AiIntelligence;
import com.subdual.ai_intelligent_service.ai.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.ai.helper.AiResponseValidator;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionResponse;
import com.subdual.ai_intelligent_service.extraction.model.ExtractedFact;
import com.subdual.ai_intelligent_service.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Primary AI intelligence implementation utilizing Spring AI, prompt templates, structured output,
 * response validation, bounded retries, and telemetry.
 */
@Component
@Primary
@Slf4j
public class SpringAiIntelligence implements AiIntelligence {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RETRIES = 2;

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final PromptTemplateService promptTemplateService;
    private final AiResponseValidator responseValidator;
    private final DeterministicAiIntelligence deterministicFallback;
    private final String geminiApiKey;

    private volatile AiExecutionMetrics latestMetrics = AiExecutionMetrics.deterministic(0L);

    public SpringAiIntelligence(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            PromptTemplateService promptTemplateService,
            AiResponseValidator responseValidator,
            DeterministicAiIntelligence deterministicFallback,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.promptTemplateService = promptTemplateService;
        this.responseValidator = responseValidator;
        this.deterministicFallback = deterministicFallback;
        this.geminiApiKey = geminiApiKey;
    }

    @Override // Interprets user requirement using Spring AI models with bounded retry.
    public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
        if (isMockMode() || chatModel == null) {
            return deterministicFallback.interpretRequirement(request);
        }

        long start = System.currentTimeMillis();
        int retries = 0;

        while (retries <= MAX_RETRIES) {
            try {
                Map<String, Object> vars = Map.of(
                        "entityType", request != null && request.entityType() != null ? request.entityType() : "PERSON",
                        "requirement", request != null && request.requirement() != null ? request.requirement().trim() : ""
                );
                String promptText = promptTemplateService.render("requirement-interpretation", vars);
                String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();

                RequirementInterpretationResponse response = parseRequirementJson(responseText);
                long duration = System.currentTimeMillis() - start;
                recordTelemetry("interpretRequirement", properties.model(), duration, 1 + retries, retries, "SUCCESS");
                return response;
            } catch (Exception ex) {
                retries++;
                log.warn("[AI_RETRY] Requirement interpretation failed attempt {}/{}: {}", retries, MAX_RETRIES, ex.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        recordTelemetry("interpretRequirement", "fallback", duration, MAX_RETRIES, MAX_RETRIES, "FALLBACK");
        return deterministicFallback.interpretRequirement(request);
    }

    @Override // Extracts grounded facts from text using Spring AI models.
    public ExtractionResponse extractFacts(ExtractionRequest request) {
        if (isMockMode() || chatModel == null) {
            return deterministicFallback.extractFacts(request);
        }

        long start = System.currentTimeMillis();
        int retries = 0;

        while (retries <= MAX_RETRIES) {
            try {
                String sourceText = request.textContent() != null ? request.textContent() : "";
                if (sourceText.length() > 6000) {
                    sourceText = sourceText.substring(0, 6000);
                }

                Map<String, Object> vars = Map.of(
                        "entityName", request.entityName() != null ? request.entityName() : "",
                        "entityType", request.entityType() != null ? request.entityType() : "PERSON",
                        "sourceText", sourceText,
                        "targetFields", request.targetFields() != null ? request.targetFields().toString() : "[]"
                );

                String promptText = promptTemplateService.render("field-extraction", vars);
                String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();

                Map<String, ExtractedFact> rawFacts = parseExtractionJson(responseText);
                Map<String, ExtractedFact> validated = responseValidator.validateExtractedFacts(rawFacts, request.targetFields(), sourceText);

                long duration = System.currentTimeMillis() - start;
                recordTelemetry("extractFacts", properties.model(), duration, 1 + retries, retries, "SUCCESS");

                return new ExtractionResponse(
                        request.entityName(),
                        request.sourceUrl(),
                        validated,
                        properties.model(),
                        duration
                );
            } catch (Exception ex) {
                retries++;
                log.warn("[AI_RETRY] Fact extraction failed attempt {}/{}: {}", retries, MAX_RETRIES, ex.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        recordTelemetry("extractFacts", "fallback", duration, MAX_RETRIES, MAX_RETRIES, "FALLBACK");
        return deterministicFallback.extractFacts(request);
    }

    @Override // Cleans and normalizes input data using Spring AI.
    public InputCleansingResponse cleanInput(InputCleansingRequest request) {
        if (isMockMode() || chatModel == null) {
            return deterministicFallback.cleanInput(request);
        }

        try {
            Map<String, Object> vars = Map.of(
                    "entityType", "PERSON",
                    "rawInput", request != null && request.rawInput() != null ? request.rawInput().toString() : "{}"
            );
            String promptText = promptTemplateService.render("input-cleansing", vars);
            String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
            return parseCleansingJson(responseText, request);
        } catch (Exception ex) {
            log.warn("Input cleansing AI call failed, falling back to deterministic: {}", ex.getMessage());
            return deterministicFallback.cleanInput(request);
        }
    }

    @Override // Returns execution metrics for the latest operation.
    public AiExecutionMetrics getLatestMetrics() {
        return latestMetrics;
    }

    private void recordTelemetry(String op, String model, long duration, int calls, int retries, String status) {
        latestMetrics = new AiExecutionMetrics(model, 0, 0, 0, duration, calls, retries, status);
        log.info("[Pipeline: AI_TELEMETRY] Operation='{}', Model='{}', Duration={}ms, Calls={}, Retries={}, Status='{}'",
                op, model, duration, calls, retries, status);
    }

    private boolean isMockMode() {
        return properties.mockMode()
                || geminiApiKey == null
                || geminiApiKey.isBlank()
                || "mock-key".equalsIgnoreCase(geminiApiKey)
                || geminiApiKey.contains("your-");
    }

    private RequirementInterpretationResponse parseRequirementJson(String rawJson) throws Exception {
        String clean = cleanJson(rawJson);
        JsonNode root = MAPPER.readTree(clean);
        List<String> fields = new ArrayList<>();
        if (root.has("requestedFields") && root.get("requestedFields").isArray()) {
            root.get("requestedFields").forEach(f -> fields.add(f.asText()));
        }
        String scope = root.has("scopeDescription") ? root.get("scopeDescription").asText() : "Parsed requirement";
        boolean isDefault = root.has("isDefaultScope") ? root.get("isDefaultScope").asBoolean(false) : false;
        return new RequirementInterpretationResponse(fields, scope, isDefault);
    }

    private Map<String, ExtractedFact> parseExtractionJson(String rawJson) throws Exception {
        String clean = cleanJson(rawJson);
        JsonNode root = MAPPER.readTree(clean);
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();

        JsonNode factsNode = root.has("facts") ? root.get("facts") : root;
        if (factsNode != null && factsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = factsNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode factObj = entry.getValue();
                String val = factObj.has("value") ? factObj.get("value").asText() : "";
                String quote = factObj.has("exactQuote") ? factObj.get("exactQuote").asText() : "";
                double conf = factObj.has("confidenceScore") ? factObj.get("confidenceScore").asDouble(0.85) : 0.85;
                facts.put(entry.getKey(), new ExtractedFact(val, quote, conf));
            }
        }
        return facts;
    }

    private InputCleansingResponse parseCleansingJson(String rawJson, InputCleansingRequest request) throws Exception {
        String clean = cleanJson(rawJson);
        JsonNode root = MAPPER.readTree(clean);
        Map<String, String> cleaned = new LinkedHashMap<>();
        Map<String, String> normalized = new LinkedHashMap<>();

        if (root.has("cleanedInput") && root.get("cleanedInput").isObject()) {
            root.get("cleanedInput").fields().forEachRemaining(e -> cleaned.put(e.getKey(), e.getValue().asText()));
        }
        if (root.has("normalizedFields") && root.get("normalizedFields").isObject()) {
            root.get("normalizedFields").fields().forEachRemaining(e -> normalized.put(e.getKey(), e.getValue().asText()));
        }

        return new InputCleansingResponse(
                cleaned.isEmpty() ? (request != null ? request.rawInput() : Map.of()) : cleaned,
                normalized,
                List.of("Cleaned via Spring AI")
        );
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }
}
