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
 * Field-specific extractor for organization / company (Task 62).
 */
@Component
public class OrganizationFieldExtractor implements FieldExtractor {

    private static final Pattern ORG_AT_PATTERN = Pattern.compile(
            "(?i)\\b(?:works at|employed at|joined|engineer at|founder of|ceo of|employed by)\\s+([A-Z][a-zA-Z0-9&\\s]{2,35}\\b(?:Inc\\.?|LLC|Ltd\\.?|Corp\\.?|Technologies|Systems|Labs|Software|Corporation)?)"
    );

    @Override
    public String fieldKey() {
        return "organization";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("organization") || lower.equals("company") || lower.equals("employer") || lower.equals("current_organization");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        Matcher m = ORG_AT_PATTERN.matcher(text);
        if (m.find()) {
            String org = m.group(1).trim();
            String snippet = RoleFieldExtractor.extractSentence(text, m.start(), m.end());

            SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
            SourceReliability rel = SourceTypeClassifier.determineReliability(type);
            ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

            return new EvidenceTuple(
                    org,
                    doc.url(),
                    snippet,
                    tier,
                    List.of(doc.url()),
                    false,
                    null,
                    type.name(),
                    "FIELD_SPECIFIC_ORG_EXTRACTOR"
            );
        }

        return null;
    }
}
