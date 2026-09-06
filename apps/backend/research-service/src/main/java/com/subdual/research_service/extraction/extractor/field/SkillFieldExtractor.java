package com.subdual.research_service.extraction.extractor.field;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.discovery.ranking.SourceTypeClassifier;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.model.SourceReliability;
import com.subdual.research_service.research.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Field extractor for skills & competencies with case/hyphen normalization and duplicate suppression.
 */
@Component
public class SkillFieldExtractor implements FieldExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern SKILL_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:Skills|Tech Stack|Expertise|Proficiencies|Core Competencies):\\s*([A-Za-z0-9+#.,/\\s-]{3,120})(?:[\\n\\r.;]|$)"
    );

    // Common standard technical competencies for safe dictionary matching
    private static final Map<String, String> CANONICAL_SKILLS = Map.ofEntries(
            Map.entry("springboot", "Spring Boot"),
            Map.entry("spring boot", "Spring Boot"),
            Map.entry("java", "Java"),
            Map.entry("typescript", "TypeScript"),
            Map.entry("javascript", "JavaScript"),
            Map.entry("react", "React"),
            Map.entry("nextjs", "Next.js"),
            Map.entry("next.js", "Next.js"),
            Map.entry("python", "Python"),
            Map.entry("golang", "Go"),
            Map.entry("go", "Go"),
            Map.entry("rust", "Rust"),
            Map.entry("c++", "C++"),
            Map.entry("docker", "Docker"),
            Map.entry("kubernetes", "Kubernetes"),
            Map.entry("k8s", "Kubernetes"),
            Map.entry("aws", "AWS"),
            Map.entry("linux", "Linux"),
            Map.entry("git", "Git"),
            Map.entry("sql", "SQL"),
            Map.entry("postgresql", "PostgreSQL"),
            Map.entry("postgres", "PostgreSQL"),
            Map.entry("mysql", "MySQL")
    );

    @Override
    public String fieldKey() {
        return "skills";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("skills") || lower.equals("tech_stack") || lower.equals("competencies")
                || lower.equals("skillset") || lower.equals("technologies");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        Set<String> normalizedSkills = new LinkedHashSet<>();
        String primaryQuote = null;

        // 1. Scan for labeled skills section
        Matcher m = SKILL_LABEL_PATTERN.matcher(text);
        if (m.find()) {
            primaryQuote = RoleFieldExtractor.extractSentence(text, m.start(), m.end());
            String rawList = m.group(1).trim();
            String[] tokens = rawList.split("[,;•|/\\n]|\\band\\b");
            for (String token : tokens) {
                String normalized = normalizeSkill(token);
                if (normalized != null) {
                    normalizedSkills.add(normalized);
                }
            }
        }

        // 2. Scan text for canonical skill mentions if list is sparse
        if (normalizedSkills.size() < 3) {
            String lowerText = text.toLowerCase(Locale.ROOT);
            CANONICAL_SKILLS.forEach((key, canonical) -> {
                Pattern wordPattern = Pattern.compile("\\b" + Pattern.quote(key) + "\\b", Pattern.CASE_INSENSITIVE);
                if (wordPattern.matcher(lowerText).find()) {
                    normalizedSkills.add(canonical);
                }
            });
        }

        if (normalizedSkills.isEmpty()) {
            return null;
        }

        try {
            List<String> list = new ArrayList<>(normalizedSkills);
            String jsonValue = MAPPER.writeValueAsString(list);
            SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
            SourceReliability rel = SourceTypeClassifier.determineReliability(type);
            ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

            return new EvidenceTuple(
                    jsonValue,
                    doc.url(),
                    primaryQuote != null ? primaryQuote : "Identified technical skills in source",
                    tier,
                    List.of(doc.url()),
                    false,
                    null,
                    type.name(),
                    "SKILL_FIELD_EXTRACTOR"
            );
        } catch (Exception ex) {
            return null;
        }
    }

    public static String normalizeSkill(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().replaceAll("^[^a-zA-Z0-9+#]+|[^a-zA-Z0-9+#]+$", "");
        if (trimmed.length() < 2 || trimmed.length() > 30) return null;

        String lower = trimmed.toLowerCase(Locale.ROOT).replaceAll("[-_]", " ");
        if (CANONICAL_SKILLS.containsKey(lower)) {
            return CANONICAL_SKILLS.get(lower);
        }
        String cleanSpaceless = lower.replaceAll("\\s+", "");
        if (CANONICAL_SKILLS.containsKey(cleanSpaceless)) {
            return CANONICAL_SKILLS.get(cleanSpaceless);
        }

        // Capitalize first letter as fallback
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
