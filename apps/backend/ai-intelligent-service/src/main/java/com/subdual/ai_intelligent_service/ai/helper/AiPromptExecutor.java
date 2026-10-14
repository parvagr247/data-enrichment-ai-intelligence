package com.subdual.ai_intelligent_service.ai.helper;

import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.exception.AiErrorClassifier;
import com.subdual.ai_intelligent_service.exception.AiErrorClassifier.AiClassification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Encapsulates ChatModel invocation, mock-mode awareness, bounded retries,
 * exponential backoff, and JSON markdown stripping following SOLID Single Responsibility.
 */
@Component
@Slf4j
public class AiPromptExecutor {

    private static final int MAX_RETRIES = 2;

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final String geminiApiKey;

    public AiPromptExecutor(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.geminiApiKey = geminiApiKey;
    }

    public boolean isMockMode() {
        return properties.mockMode()
                || geminiApiKey == null
                || geminiApiKey.isBlank()
                || "mock-key".equalsIgnoreCase(geminiApiKey)
                || geminiApiKey.contains("your-")
                || chatModel == null;
    }

    public String getModelName() {
        return properties.model();
    }

    /**
     * Executes the given prompt text with bounded retries and exponential backoff.
     * Returns Optional with stripped JSON text if successful, or Optional.empty() on failure/mock mode.
     */
    public Optional<String> executePrompt(String operationName, String promptText, String entityName) {
        if (isMockMode()) {
            return Optional.empty();
        }

        long startMs = System.currentTimeMillis();
        int attempt = 0;

        while (attempt <= MAX_RETRIES) {
            attempt++;
            try {
                log.info("[AI_CALL] operation='{}' entity='{}' model='{}' attempt={}/{}",
                        operationName, entityName, properties.model(), attempt, MAX_RETRIES + 1);

                String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
                String cleaned = cleanJson(responseText);

                log.info("[AI_SUCCESS] operation='{}' entity='{}' durationMs={}",
                        operationName, entityName, System.currentTimeMillis() - startMs);

                return Optional.of(cleaned);
            } catch (Exception ex) {
                AiClassification classification = AiErrorClassifier.classify(ex);
                if (shouldRetry(classification, attempt)) {
                    pauseForRetry(operationName, entityName, attempt, classification);
                } else {
                    log.warn("[AI_FALLBACK] operation='{}' entity='{}' category='{}' httpStatus={} reason='{}'",
                            operationName, entityName, classification.category(), classification.httpStatusCode(), classification.sanitizedMessage());
                    break;
                }
            }
        }

        return Optional.empty();
    }

    private boolean shouldRetry(AiClassification classification, int attempt) {
        boolean isDailyQuotaExhausted = classification.sanitizedMessage().toLowerCase(Locale.ROOT).contains("free_tier_requests")
                || classification.sanitizedMessage().toLowerCase(Locale.ROOT).contains("generaterequestsperday");
        return classification.isRetryable() && !isDailyQuotaExhausted && attempt <= MAX_RETRIES;
    }

    private void pauseForRetry(String operationName, String entityName, int attempt, AiClassification classification) {
        long backoffMs = attempt * 500L;
        log.warn("[AI_RETRY] operation='{}' entity='{}' attempt={}/{} category='{}' retrying in {}ms: {}",
                operationName, entityName, attempt, MAX_RETRIES + 1, classification.category(), backoffMs, classification.sanitizedMessage());
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public String cleanJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }
}
