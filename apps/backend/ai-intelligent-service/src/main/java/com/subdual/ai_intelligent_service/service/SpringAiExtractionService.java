package com.subdual.ai_intelligent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.dto.ExtractedFact;
import com.subdual.ai_intelligent_service.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.dto.ExtractionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SpringAiExtractionService implements ExtractionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PROMPT_TEXT_LENGTH = 6000;
    private static final double DEFAULT_CONFIDENCE_SCORE = 0.85;

    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:is|as)\\s+(?:a|an)?\\s+([A-Z][a-zA-Z\\s]{2,40}\\b(?:Engineer|Architect|Director|Developer|Manager|Scientist|Lead|Founder|CEO|CTO))"
    );
    private static final Pattern ORG_PATTERN = Pattern.compile(
            "(?i)\\b(?:at|for)\\s+([A-Z][a-zA-Z0-9&\\s]{2,30})\\b"
    );
    private static final Pattern LICENSE_PATTERN = Pattern.compile(
            "(?i)\\b(Apache[-\\s]?2\\.0|MIT|GPL|BSD|Mozilla Public License)\\b"
    );

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final String geminiApiKey;

    public SpringAiExtractionService(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.geminiApiKey = geminiApiKey;
    }

    @Override
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
        if (!isMockMode() && chatModel != null) {
            try {
                Map<String, ExtractedFact> facts = extractViaSpringAi(request);
                return new ExtractionExecution(facts, properties.model());
            } catch (Exception ex) {
                log.warn("Spring AI extraction failed ({}), falling back to deterministic extraction", ex.getMessage());
                return new ExtractionExecution(extractDeterministically(request), "deterministic-fallback");
            }
        }

        log.info("Executing deterministic fact extraction for entity: '{}'", request.entityName());
        return new ExtractionExecution(extractDeterministically(request), "deterministic-rule-engine");
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
        String rawResponse = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
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
            return extractDeterministically(request);
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

            // Zero-hallucination guard: quote must actually appear verbatim in the source text
            if (lowerContent.contains(quote.toLowerCase(Locale.ROOT).trim())) {
                facts.put(entry.getKey(), new ExtractedFact(val, quote, score));
            }
        }
    }

    private Map<String, ExtractedFact> extractDeterministically(ExtractionRequest request) {
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();
        String text = request.textContent();
        if (text == null || text.isBlank()) {
            return facts;
        }

        String lowerName = request.entityName().trim().toLowerCase(Locale.ROOT);
        String[] sentences = text.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 10) {
                continue;
            }
            extractSentenceFacts(trimmed, lowerName, facts);
        }

        return facts;
    }

    private void extractSentenceFacts(String sentence, String lowerName, Map<String, ExtractedFact> facts) {
        String lowerSentence = sentence.toLowerCase(Locale.ROOT);

        tryExtractDescription(sentence, lowerSentence, lowerName, facts);
        tryExtractRole(sentence, lowerSentence, lowerName, facts);
        tryExtractOrganization(sentence, lowerSentence, lowerName, facts);
        tryExtractLicense(sentence, lowerSentence, facts);
    }

    private void tryExtractDescription(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("description") || !lowerSentence.contains(lowerName)) {
            return;
        }
        if (lowerSentence.contains("is a") || lowerSentence.contains("is an")
                || lowerSentence.contains("makes it easy") || lowerSentence.contains("provides")
                || lowerSentence.contains("platform") || lowerSentence.contains("helps you")) {
            facts.put("description", new ExtractedFact(sentence, sentence, 0.92));
        }
    }

    private void tryExtractRole(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("currentRole") || !lowerSentence.contains(lowerName)) {
            return;
        }
        Matcher m = ROLE_PATTERN.matcher(sentence);
        if (m.find()) {
            String role = m.group(1).trim();
            facts.put("currentRole", new ExtractedFact(role, sentence, 0.90));
        }
    }

    private void tryExtractOrganization(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("organization") || !lowerSentence.contains(lowerName)) {
            return;
        }
        Matcher m = ORG_PATTERN.matcher(sentence);
        if (m.find()) {
            String org = m.group(1).trim();
            facts.put("organization", new ExtractedFact(org, sentence, 0.85));
        }
    }

    private void tryExtractLicense(String sentence, String lowerSentence, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("license") || !lowerSentence.contains("license")) {
            return;
        }
        Matcher m = LICENSE_PATTERN.matcher(sentence);
        if (m.find()) {
            facts.put("license", new ExtractedFact(m.group(1), sentence, 0.95));
        }
    }

    private record ExtractionExecution(Map<String, ExtractedFact> facts, String modelUsed) {}
}
