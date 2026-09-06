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
 * Field-specific extractor for role / job title (Task 62).
 */
@Component
public class RoleFieldExtractor implements FieldExtractor {

    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:is|as|works as)\\s+(?:a|an)?\\s+([A-Z][a-zA-Z\\s]{2,35}\\b(?:Engineer|Architect|Director|Developer|Manager|Scientist|Lead|Founder|CEO|CTO|Researcher|Designer|Consultant))"
    );

    private static final Pattern TITLE_AT_ORG_PATTERN = Pattern.compile(
            "(?i)\\b([A-Z][a-zA-Z\\s]{2,30}\\b(?:Engineer|Architect|Director|Developer|Manager|Scientist|Lead|Founder|CEO|CTO))\\s+(?:at|@|with)\\s+([A-Z][a-zA-Z0-9&\\s]{2,30})"
    );

    @Override
    public String fieldKey() {
        return "role";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("role") || lower.equals("currentrole") || lower.equals("position") || lower.equals("title");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        Matcher m1 = TITLE_AT_ORG_PATTERN.matcher(text);
        if (m1.find()) {
            String role = m1.group(1).trim();
            String snippet = extractSentence(text, m1.start(), m1.end());
            return buildTuple(role, doc, target, snippet);
        }

        Matcher m2 = ROLE_PATTERN.matcher(text);
        if (m2.find()) {
            String role = m2.group(1).trim();
            String snippet = extractSentence(text, m2.start(), m2.end());
            return buildTuple(role, doc, target, snippet);
        }

        return null;
    }

    private EvidenceTuple buildTuple(String value, ExtractedDocument doc, ResearchTarget target, String snippet) {
        SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
        SourceReliability rel = SourceTypeClassifier.determineReliability(type);
        ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

        return new EvidenceTuple(
                value,
                doc.url(),
                snippet,
                tier,
                List.of(doc.url()),
                false,
                null,
                type.name(),
                "FIELD_SPECIFIC_ROLE_EXTRACTOR"
        );
    }

    static String extractSentence(String text, int start, int end) {
        int sentenceStart = Math.max(0, text.lastIndexOf('.', start) + 1);
        int sentenceEnd = text.indexOf('.', end);
        if (sentenceEnd == -1) sentenceEnd = Math.min(text.length(), end + 100);
        else sentenceEnd = Math.min(text.length(), sentenceEnd + 1);

        String raw = text.substring(sentenceStart, sentenceEnd).trim();
        return raw.length() > 250 ? raw.substring(0, 250) + "..." : raw;
    }
}
