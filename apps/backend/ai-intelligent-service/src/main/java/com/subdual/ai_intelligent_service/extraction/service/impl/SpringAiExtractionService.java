package com.subdual.ai_intelligent_service.extraction.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.exception.AiErrorClassifier;
import com.subdual.ai_intelligent_service.exception.AiErrorClassifier.AiClassification;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionResponse;
import com.subdual.ai_intelligent_service.extraction.model.ExtractedFact;
import com.subdual.ai_intelligent_service.extraction.service.ExtractionService;
import com.subdual.ai_intelligent_service.extraction.service.helper.DeterministicExtractionHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SpringAiExtractionService implements ExtractionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PROMPT_TEXT_LENGTH = 6000;
    private static final double DEFAULT_CONFIDENCE_SCORE = 0.85;

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final DeterministicExtractionHelper deterministicHelper;
    private final String geminiApiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public SpringAiExtractionService(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            DeterministicExtractionHelper deterministicHelper,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.deterministicHelper = deterministicHelper;
        this.geminiApiKey = geminiApiKey;
    }

    public SpringAiExtractionService(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this(chatModel, properties, new DeterministicExtractionHelper(), geminiApiKey);
    }

    @Override // Extracts grounded structured facts from text content.
    public ExtractionResponse extractFacts(ExtractionRequest request) {
        long startTime = System.currentTimeMillis();
        ExtractionExecution execution = executeExtraction(request);
        long executionTimeMs = System.currentTimeMillis() - startTime;

        return new ExtractionResponse(
                request.entityName(),
                request.sourceUrl(),
                execution.facts(),
                execution.modelUsed(),
                executionTimeMs
        );
    }

    private ExtractionExecution executeExtraction(ExtractionRequest request) {
        long startTime = System.currentTimeMillis();
        if (!isMockMode() && chatModel != null) {
            return executeAiWithRetry(request, startTime);
        }

        Map<String, ExtractedFact> facts = deterministicHelper.extractDeterministically(request);
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[AI_EXTRACTION] entity='{}' source='{}' model='deterministic-rule-engine' provider='local' status='SUCCESS' factsExtracted={} durationMs={}",
                request.entityName(), request.sourceUrl(), facts.size(), durationMs);
        return new ExtractionExecution(facts, "deterministic-rule-engine");
    }

    private ExtractionExecution executeAiWithRetry(ExtractionRequest request, long startTime) {
        int maxRetries = 2;
        int attempt = 0;
        while (attempt <= maxRetries) {
            attempt++;
            try {
                Map<String, ExtractedFact> facts = extractViaSpringAi(request);
                long durationMs = System.currentTimeMillis() - startTime;
                log.info("[AI_EXTRACTION] entity='{}' source='{}' model='{}' provider='google-genai' status='SUCCESS' factsExtracted={} durationMs={}",
                        request.entityName(), request.sourceUrl(), properties.model(), facts.size(), durationMs);
                return new ExtractionExecution(facts, properties.model());
            } catch (Exception ex) {
                AiClassification classification = AiErrorClassifier.classify(ex);
                if (shouldRetry(classification, attempt, maxRetries)) {
                    pauseForRetry(request, attempt, maxRetries, classification);
                } else {
                    return handleAiFallback(request, classification, startTime);
                }
            }
        }
        return new ExtractionExecution(deterministicHelper.extractDeterministically(request), "deterministic-fallback");
    }

    private boolean shouldRetry(AiClassification classification, int attempt, int maxRetries) {
        boolean isDailyQuotaExhausted = classification.sanitizedMessage().toLowerCase(Locale.ROOT).contains("free_tier_requests")
                || classification.sanitizedMessage().toLowerCase(Locale.ROOT).contains("generaterequestsperday");
        return classification.isRetryable() && !isDailyQuotaExhausted && attempt <= maxRetries;
    }

    private void pauseForRetry(ExtractionRequest request, int attempt, int maxRetries, AiClassification classification) {
        long backoffMs = attempt * 500L;
        log.warn("[AI_EXTRACTION] entity='{}' source='{}' model='{}' attempt={}/{} errorCategory='{}' retryable=true retrying in {}ms (reason: {})",
                request.entityName(), request.sourceUrl(), properties.model(), attempt, maxRetries + 1,
                classification.category(), backoffMs, classification.sanitizedMessage());
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private ExtractionExecution handleAiFallback(ExtractionRequest request, AiClassification classification, long startTime) {
        long durationMs = System.currentTimeMillis() - startTime;
        log.warn("[AI_EXTRACTION] entity='{}' source='{}' model='{}' provider='google-genai' status='FALLBACK' category='{}' httpStatus={} reason='{}' fallback='DETERMINISTIC' durationMs={}",
                request.entityName(), request.sourceUrl(), properties.model(),
                classification.category(), classification.httpStatusCode(), classification.sanitizedMessage(), durationMs);
        return new ExtractionExecution(deterministicHelper.extractDeterministically(request), "deterministic-fallback");
    }

    private boolean isMockMode() {
        return properties.mockMode()
                || geminiApiKey == null
                || geminiApiKey.isBlank()
                || "mock-key".equalsIgnoreCase(geminiApiKey)
                || geminiApiKey.contains("your-");
    }

    private Map<String, ExtractedFact> extractViaSpringAi(ExtractionRequest request) {
        String promptText = buildPromptText(request);
        String rawResponse;
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    chatModel.call(new Prompt(promptText)).getResult().getOutput().getText()
            );
            rawResponse = future.get(15, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (java.util.concurrent.TimeoutException te) {
            throw new java.util.concurrent.CompletionException(new java.net.SocketTimeoutException("Gemini AI extraction timed out after 15 seconds"));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini AI extraction interrupted", ie);
        }
        return parseSpringAiResponse(rawResponse, request);
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
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private Map<String, ExtractedFact> parseSpringAiResponse(String rawResponse, ExtractionRequest request) {
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();

        try {
            String cleaned = rawResponse.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();
            JsonNode root = MAPPER.readTree(cleaned);
            String lowerContent = request.textContent().toLowerCase(Locale.ROOT);

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

    private record ExtractionExecution(Map<String, ExtractedFact> facts, String modelUsed) {}
}
