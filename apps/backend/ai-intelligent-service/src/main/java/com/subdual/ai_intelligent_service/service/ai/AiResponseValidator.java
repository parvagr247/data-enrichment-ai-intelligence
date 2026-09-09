package com.subdual.ai_intelligent_service.service.ai;

import com.subdual.ai_intelligent_service.dto.ExtractedFact;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates AI structured output against schema constraints and target field boundaries.
 */
@Component
public class AiResponseValidator {

    public Map<String, ExtractedFact> validateExtractedFacts(
            Map<String, ExtractedFact> rawFacts,
            List<String> targetFields,
            String sourceText
    ) {
        if (rawFacts == null || rawFacts.isEmpty()) {
            return Map.of();
        }

        Set<String> allowedFields = new HashSet<>();
        if (targetFields != null && !targetFields.isEmpty()) {
            targetFields.forEach(f -> allowedFields.add(f.toLowerCase(Locale.ROOT).trim()));
        }

        String lowerSource = sourceText != null ? sourceText.toLowerCase(Locale.ROOT) : "";
        Map<String, ExtractedFact> validated = new LinkedHashMap<>();

        for (Map.Entry<String, ExtractedFact> entry : rawFacts.entrySet()) {
            String key = entry.getKey();
            ExtractedFact fact = entry.getValue();

            if (key == null || key.isBlank() || fact == null) {
                continue;
            }

            String lowerKey = key.toLowerCase(Locale.ROOT).trim();
            if (!allowedFields.isEmpty() && !allowedFields.contains(lowerKey)) {
                continue;
            }

            if (fact.value() == null || fact.value().isBlank()
                    || fact.value().equalsIgnoreCase("null")
                    || fact.value().equalsIgnoreCase("unknown")
                    || fact.value().equalsIgnoreCase("undefined")) {
                continue;
            }

            double confidence = fact.confidenceScore();
            if (Double.isNaN(confidence) || confidence < 0.0) {
                confidence = 0.50;
            } else if (confidence > 1.0) {
                confidence = 1.0;
            }

            String quote = fact.exactQuote();
            if (!lowerSource.isBlank()) {
                boolean quoteInSource = quote != null && !quote.isBlank() && lowerSource.contains(quote.toLowerCase(Locale.ROOT).trim());
                boolean valInSource = lowerSource.contains(fact.value().toLowerCase(Locale.ROOT).trim());
                if (!quoteInSource && !valInSource) {
                    continue;
                }
            }

            validated.put(key, new ExtractedFact(fact.value().trim(), quote != null ? quote.trim() : "", confidence));
        }

        return validated;
    }
}
