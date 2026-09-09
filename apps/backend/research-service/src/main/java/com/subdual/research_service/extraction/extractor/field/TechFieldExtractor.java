package com.subdual.research_service.extraction.extractor.field;

import com.subdual.research_service.research.api.EvidenceTuple;
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
 * Field-specific extractor for technologies / skills / programming languages.
 */
@Component
public class TechFieldExtractor implements FieldExtractor {

    private static final Pattern TECH_PATTERN = Pattern.compile(
            "(?i)\\b(?:written in|built with|developed in|tech stack:|skills:|proficient in|expertise in)\\s+([a-zA-Z0-9#+.,/\\s]{2,40}\\b(?:Java|Python|TypeScript|JavaScript|Go|Rust|C\\+\\+|C#|React|Spring|Docker|Kubernetes|AWS)?)"
    );

    @Override
    public String fieldKey() {
        return "technologies";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("skills") || lower.equals("technologies") || lower.equals("tech_stack")
                || lower.equals("primary_language") || lower.equals("languages");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        Matcher m = TECH_PATTERN.matcher(text);
        if (m.find()) {
            String tech = m.group(1).trim();
            String snippet = RoleFieldExtractor.extractSentence(text, m.start(), m.end());

            SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
            SourceReliability rel = SourceTypeClassifier.determineReliability(type);
            ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

            return new EvidenceTuple(
                    tech,
                    doc.url(),
                    snippet,
                    tier,
                    List.of(doc.url()),
                    false,
                    null,
                    type.name(),
                    "FIELD_SPECIFIC_TECH_EXTRACTOR"
            );
        }

        return null;
    }
}
