package com.subdual.ai_intelligent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.dto.ExtractedFact;
import com.subdual.ai_intelligent_service.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.dto.ExtractionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class SpringAiExtractionService implements ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiExtractionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final String geminiApiKey;

    @Autowired
    public SpringAiExtractionService(
            @Autowired(required = false) ChatModel chatModel,
            AiProperties properties,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.geminiApiKey = geminiApiKey;
    }

    @Override
    public ExtractionResponse extractFacts(ExtractionRequest request) {
        long startTime = System.currentTimeMillis();

        boolean isMock = properties.mockMode()
                || geminiApiKey == null
                || geminiApiKey.isBlank()
                || "mock-key".equalsIgnoreCase(geminiApiKey)
                || geminiApiKey.contains("your-");

        Map<String, ExtractedFact> facts;
        String modelUsed;

        if (!isMock && chatModel != null) {
            try {
                facts = extractViaSpringAi(request);
                modelUsed = properties.model();
            } catch (Exception ex) {
                log.warn("Spring AI extraction failed ({}), falling back to deterministic extraction", ex.getMessage());
                facts = extractDeterministically(request);
                modelUsed = "deterministic-fallback";
            }
        } else {
            log.info("Executing deterministic fact extraction for entity: '{}'", request.entityName());
            facts = extractDeterministically(request);
            modelUsed = "deterministic-rule-engine";
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;
        return new ExtractionResponse(
                request.entityName(),
                request.sourceUrl(),
                facts,
                modelUsed,
                executionTimeMs
        );
    }

    private Map<String, ExtractedFact> extractViaSpringAi(ExtractionRequest request) {
        String promptText = String.format("""
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
                request.entityType() != null ? request.entityType() : "OTHER",
                request.targetFields(),
                request.textContent().length() > 6000 ? request.textContent().substring(0, 6000) : request.textContent()
        );

        String rawResponse = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();

        try {
            // Strip any Markdown code block wrapping if present
            String cleaned = rawResponse.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "").trim();
            JsonNode root = MAPPER.readTree(cleaned);
            root.fields().forEachRemaining(entry -> {
                JsonNode factNode = entry.getValue();
                if (factNode.has("value") && factNode.has("exactQuote")) {
                    String val = factNode.get("value").asText();
                    String quote = factNode.get("exactQuote").asText();
                    double score = factNode.has("confidenceScore") ? factNode.get("confidenceScore").asDouble() : 0.85;
                    // Zero-hallucination guard: quote must actually appear in the text
                    if (request.textContent().toLowerCase(Locale.ROOT).contains(quote.toLowerCase(Locale.ROOT).trim())) {
                        facts.put(entry.getKey(), new ExtractedFact(val, quote, score));
                    }
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to parse Spring AI JSON response: {}", ex.getMessage());
            return extractDeterministically(request);
        }

        return facts;
    }

    private Map<String, ExtractedFact> extractDeterministically(ExtractionRequest request) {
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();
        String text = request.textContent();
        if (text == null || text.isBlank()) {
            return facts;
        }

        String entityName = request.entityName().trim();
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerName = entityName.toLowerCase(Locale.ROOT);

        // Break text into sentences
        String[] sentences = text.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 10) continue;
            String lowerSentence = trimmed.toLowerCase(Locale.ROOT);

            // 1. Description / Summary
            if (!facts.containsKey("description") && lowerSentence.contains(lowerName)) {
                if (lowerSentence.contains("is a") || lowerSentence.contains("is an")
                        || lowerSentence.contains("makes it easy") || lowerSentence.contains("provides")
                        || lowerSentence.contains("platform") || lowerSentence.contains("helps you")) {
                    facts.put("description", new ExtractedFact(trimmed, trimmed, 0.92));
                }
            }

            // 2. Role Extraction
            if (!facts.containsKey("currentRole") && lowerSentence.contains(lowerName)) {
                Pattern rolePattern = Pattern.compile("(?i)\\b(?:is|as)\\s+(?:a|an)?\\s+([A-Z][a-zA-Z\\s]{2,40}\\b(?:Engineer|Architect|Director|Developer|Manager|Scientist|Lead|Founder|CEO|CTO))");
                Matcher m = rolePattern.matcher(trimmed);
                if (m.find()) {
                    String role = m.group(1).trim();
                    facts.put("currentRole", new ExtractedFact(role, trimmed, 0.90));
                }
            }

            // 3. Organization Extraction
            if (!facts.containsKey("organization")) {
                Pattern orgPattern = Pattern.compile("(?i)\\b(?:at|for)\\s+([A-Z][a-zA-Z0-9&\\s]{2,30})\\b");
                Matcher m = orgPattern.matcher(trimmed);
                if (m.find() && lowerSentence.contains(lowerName)) {
                    String org = m.group(1).trim();
                    facts.put("organization", new ExtractedFact(org, trimmed, 0.85));
                }
            }

            // 4. Repository / License facts
            if (!facts.containsKey("license") && lowerSentence.contains("license")) {
                Pattern licensePattern = Pattern.compile("(?i)\\b(Apache[-\\s]?2\\.0|MIT|GPL|BSD|Mozilla Public License)\\b");
                Matcher m = licensePattern.matcher(trimmed);
                if (m.find()) {
                    facts.put("license", new ExtractedFact(m.group(1), trimmed, 0.95));
                }
            }
        }

        return facts;
    }
}
