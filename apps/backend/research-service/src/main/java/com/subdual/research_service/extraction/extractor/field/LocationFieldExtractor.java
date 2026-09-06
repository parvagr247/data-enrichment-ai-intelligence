package com.subdual.research_service.extraction.extractor.field;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.discovery.ranking.SourceTypeClassifier;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.model.SourceReliability;
import com.subdual.research_service.research.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Field-specific extractor for location / headquarters (Task 62).
 */
@Component
public class LocationFieldExtractor implements FieldExtractor {

    private static final Pattern LOC_PATTERN = Pattern.compile(
            "(?i)\\b(?:based in|located in|headquartered in|living in|residing in)\\s+([A-Z][a-zA-Z0-9,\\s]{2,35}\\b(?:USA|UK|Canada|India|Germany|France|Australia|California|New York|London|Bengaluru|San Francisco)?)"
    );

    @Override
    public String fieldKey() {
        return "location";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("location") || lower.equals("city") || lower.equals("country")
                || lower.equals("headquarters") || lower.equals("based_in");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        Matcher m = LOC_PATTERN.matcher(text);
        if (m.find()) {
            String location = m.group(1).trim();
            String snippet = RoleFieldExtractor.extractSentence(text, m.start(), m.end());

            SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
            SourceReliability rel = SourceTypeClassifier.determineReliability(type);
            ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

            return new EvidenceTuple(
                    location,
                    doc.url(),
                    snippet,
                    tier,
                    List.of(doc.url()),
                    false,
                    null,
                    type.name(),
                    "FIELD_SPECIFIC_LOC_EXTRACTOR"
            );
        }

        return null;
    }
}
