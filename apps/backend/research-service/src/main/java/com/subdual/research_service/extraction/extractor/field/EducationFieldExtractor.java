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
 * Field-specific extractor for education / degree / alumni institution (Task 62).
 */
@Component
public class EducationFieldExtractor implements FieldExtractor {

    private static final Pattern EDU_PATTERN = Pattern.compile(
            "(?i)\\b(?:graduated from|degree from|studied at|alumnus of|alumna of|holds a degree from|bachelor's from|master's from|phd from)\\s+([A-Z][a-zA-Z0-9&,\\s]{2,40}\\b(?:University|College|Institute|School|Academy)?)"
    );

    @Override
    public String fieldKey() {
        return "education";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("education") || lower.equals("degree") || lower.equals("university")
                || lower.equals("college") || lower.equals("alumni");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        Matcher m = EDU_PATTERN.matcher(text);
        if (m.find()) {
            String institution = m.group(1).trim();
            String snippet = RoleFieldExtractor.extractSentence(text, m.start(), m.end());

            SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
            SourceReliability rel = SourceTypeClassifier.determineReliability(type);
            ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

            return new EvidenceTuple(
                    institution,
                    doc.url(),
                    snippet,
                    tier,
                    List.of(doc.url()),
                    false,
                    null,
                    type.name(),
                    "FIELD_SPECIFIC_EDU_EXTRACTOR"
            );
        }

        return null;
    }
}
