package com.subdual.ai_intelligent_service.extraction.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.ai.helper.AiPromptExecutor;
import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.response.ExtractionResponse;
import com.subdual.ai_intelligent_service.extraction.model.ExtractedFact;
import com.subdual.ai_intelligent_service.extraction.service.ExtractionService;
import com.subdual.ai_intelligent_service.extraction.service.helper.DeterministicExtractionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Clean, minimalistic implementation of ExtractionService using AiPromptExecutor and zero-hallucination guard.
 */
@Service
@Slf4j
public class SpringAiExtractionService implements ExtractionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PROMPT_TEXT_LENGTH = 6000;
    private static final double DEFAULT_CONFIDENCE_SCORE = 0.85;

    private final AiPromptExecutor promptExecutor;
    private final DeterministicExtractionHelper deterministicHelper;

    @org.springframework.beans.factory.annotation.Autowired
    public SpringAiExtractionService(AiPromptExecutor promptExecutor, DeterministicExtractionHelper deterministicHelper) {
        this.promptExecutor = promptExecutor;
        this.deterministicHelper = deterministicHelper;
    }

    public SpringAiExtractionService(
            java.util.Optional<org.springframework.ai.chat.model.ChatModel> chatModel,
            com.subdual.ai_intelligent_service.configuration.AiProperties properties,
            String apiKey,
            DeterministicExtractionHelper deterministicHelper,
            com.subdual.ai_intelligent_service.prompt.PromptTemplateService promptService,
            com.subdual.ai_intelligent_service.ai.helper.AiResponseValidator validator
    ) {
        this(new AiPromptExecutor(chatModel, properties, apiKey), deterministicHelper);
    }

    @Override
    public ExtractionResponse extractFacts(ExtractionRequest request) {
        long startTime = System.currentTimeMillis();

        if (request != null) {
            String promptText = buildPromptText(request);
            Optional<String> aiResponse = promptExecutor.executePrompt("FACT_EXTRACTION", promptText, request.entityName());

            if (aiResponse.isPresent()) {
                Map<String, ExtractedFact> facts = parseSpringAiResponse(aiResponse.get(), request);
                long executionTimeMs = System.currentTimeMillis() - startTime;
                return new ExtractionResponse(
                        request.entityName(),
                        request.sourceUrl(),
                        facts,
                        promptExecutor.getModelName(),
                        executionTimeMs
                );
            }
        }

        Map<String, ExtractedFact> facts = deterministicHelper.extractDeterministically(request);
        long executionTimeMs = System.currentTimeMillis() - startTime;
        return new ExtractionResponse(
                request != null ? request.entityName() : null,
                request != null ? request.sourceUrl() : null,
                facts,
                "deterministic-fallback",
                executionTimeMs
        );
    }

    private String buildPromptText(ExtractionRequest request) {
        String entityType = request.entityType() != null ? request.entityType() : "OTHER";
        String truncatedText = truncateText(request.textContent(), MAX_PROMPT_TEXT_LENGTH);

        return String.format("""
                You are a strict data extraction system. Extract verified facts about the entity '%s' (type: %s) from the text below.
                
                TARGET FIELDS TO LOOK FOR: %s
                
                RULES:
                1. Extract ONLY facts explicitly and verbatim asserted in the text.
                2. DO NOT hallucinate, infer, assume, or extrapolate.
                3. If a fact is not present in the text, DO NOT return that field.
                4. For each fact, return a JSON object with:
                   - "value": the extracted fact value (e.g. "Principal Engineer")
                   - "exactQuote": the verbatim sentence from the text that proves this fact
                   - "confidenceScore": a number between 0.0 and 1.0 representing directness of proof
                
                Return strictly valid JSON in the format:
                {
                   "fieldName": { "value": "...", "exactQuote": "...", "confidenceScore": 0.95 }
                }
                
                TEXT:
                %s
                """,
                request.entityName(),
                entityType,
                request.targetFields(),
                truncatedText
        );
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private Map<String, ExtractedFact> parseSpringAiResponse(String rawResponse, ExtractionRequest request) {
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();

        try {
            JsonNode root = MAPPER.readTree(rawResponse);
            String lowerContent = request.textContent() != null ? request.textContent().toLowerCase(Locale.ROOT) : "";

            root.fields().forEachRemaining(entry -> parseFactEntry(entry, lowerContent, facts));
        } catch (Exception ex) {
            log.warn("Failed to parse Spring AI JSON response: {}", ex.getMessage());
            return deterministicHelper.extractDeterministically(request);
        }

        return facts;
    }

    private void parseFactEntry(Map.Entry<String, JsonNode> entry, String lowerContent, Map<String, ExtractedFact> facts) {
        JsonNode factNode = entry.getValue();
        if (factNode.has("value") && factNode.has("exactQuote")) {
            String val = factNode.get("value").asText();
            String quote = factNode.get("exactQuote").asText();
            double score = factNode.has("confidenceScore")
                    ? factNode.get("confidenceScore").asDouble()
                    : DEFAULT_CONFIDENCE_SCORE;

            // Zero-hallucination guard: quote must actually appear verbatim in source text.
            if (lowerContent.contains(quote.toLowerCase(Locale.ROOT).trim())) {
                facts.put(entry.getKey(), new ExtractedFact(val, quote, score));
            }
        }
    }
}
