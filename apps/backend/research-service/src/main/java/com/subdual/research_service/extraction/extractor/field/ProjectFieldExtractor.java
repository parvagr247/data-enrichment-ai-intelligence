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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Field extractor for notable open-source and commercial projects.
 */
@Component
public class ProjectFieldExtractor implements FieldExtractor {

    private static final Pattern PROJECT_PATTERN = Pattern.compile(
            "(?i)(?:Creator of|Author of|Maintainer of|Initiated|Founded|Lead on|Built)\\s+([A-Za-z0-9_.-]{2,40}\\b(?:kernel|framework|library|project|tool|engine)?)"
    );

    @Override
    public String fieldKey() {
        return "projects";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("projects") || lower.equals("notable_projects") || lower.equals("open_source")
                || lower.equals("portfolio") || lower.equals("creations");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        List<Map<String, Object>> projects = new ArrayList<>();
        String primaryQuote = null;

        Matcher m = PROJECT_PATTERN.matcher(text);
        while (m.find() && projects.size() < 5) {
            String projName = m.group(1).trim();
            if (projName.length() >= 2 && !projName.equalsIgnoreCase("the") && !projName.equalsIgnoreCase("a")) {
                String snippet = RoleFieldExtractor.extractSentence(text, m.start(), m.end());
                if (primaryQuote == null) primaryQuote = snippet;

                Map<String, Object> proj = new LinkedHashMap<>();
                proj.put("name", projName);
                proj.put("role", m.group(0).trim());
                proj.put("description", snippet);
                proj.put("evidence", snippet);

                boolean exists = projects.stream().anyMatch(p -> projName.equalsIgnoreCase(String.valueOf(p.get("name"))));
                if (!exists) {
                    projects.add(proj);
                }
            }
        }

        if (projects.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < projects.size(); i++) {
            Map<String, Object> p = projects.get(i);
            if (i > 0) sb.append(" | ");
            sb.append(p.get("name"));
            if (p.get("description") != null) {
                sb.append(": ").append(p.get("description"));
            }
        }
        String formattedValue = sb.toString();

        SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
        SourceReliability rel = SourceTypeClassifier.determineReliability(type);
        ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

        return new EvidenceTuple(
                formattedValue,
                doc.url(),
                primaryQuote != null ? primaryQuote : "Notable projects extracted",
                tier,
                List.of(doc.url()),
                false,
                null,
                type.name(),
                "PROJECT_FIELD_EXTRACTOR"
        );
    }
}
