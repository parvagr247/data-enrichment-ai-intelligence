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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured field extractor for education, degrees, and academic institutions.
 */
@Component
public class EducationFieldExtractor implements FieldExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Matches: "graduated from MIT with a degree in Computer Science" or "holds a Master of Science in AI from Stanford"
    private static final Pattern EDU_FULL_PATTERN = Pattern.compile(
            "(?i)(?:graduated from|degree from|studied at|alumnus of|alumna of|holds a degree from|bachelor's from|master's from|phd from)\\s+([A-Z][a-zA-Z0-9&,\\s]{1,40}?\\b(?:University|College|Institute|School|Academy|MIT|Stanford|Harvard|Berkeley|Caltech|CMU)?)(?:\\s+(?:with|in)\\s+(?:a\\s+)?([A-Za-z0-9\\s]{3,35}?\\b(?:degree|B\\.S\\.|M\\.S\\.|Ph\\.D\\.|Bachelor|Master|Doctorate)?)(?:\\s+in\\s+([A-Za-z0-9\\s]{3,35}))?)?(?:\\s*[-–•|,]?\\s*\\(?((?:19|20)\\d{2})\\s*(?:-|–|to)\\s*((?:19|20)\\d{2})\\)?)?"
    );

    private static final Pattern DEGREE_AT_INSTITUTION_PATTERN = Pattern.compile(
            "(?i)(B\\.S\\.|M\\.S\\.|Ph\\.D\\.|Bachelor of [A-Za-z]+|Master of [A-Za-z]+|Doctor of [A-Za-z]+)\\s+(?:in\\s+([A-Za-z0-9\\s]{3,35}))?\\s+(?:from|at)\\s+([A-Z][a-zA-Z0-9&,\\s]{2,40}\\b(?:University|College|Institute|School)?)"
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
                || lower.equals("college") || lower.equals("alumni") || lower.equals("academic");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        List<Map<String, Object>> educationList = new ArrayList<>();
        String primaryQuote = null;

        // 1. Scan for EDU_FULL_PATTERN
        Matcher m1 = EDU_FULL_PATTERN.matcher(text);
        while (m1.find() && educationList.size() < 4) {
            String institution = m1.group(1) != null ? m1.group(1).trim() : null;
            String degree = m1.group(2) != null ? m1.group(2).trim() : null;
            String field = m1.group(3) != null ? m1.group(3).trim() : null;
            String startYear = m1.group(4) != null ? m1.group(4).trim() : null;
            String endYear = m1.group(5) != null ? m1.group(5).trim() : null;

            if (isValidInstitution(institution)) {
                String snippet = RoleFieldExtractor.extractSentence(text, m1.start(), m1.end());
                if (primaryQuote == null) {
                    primaryQuote = snippet;
                }
                Map<String, Object> edu = new LinkedHashMap<>();
                edu.put("institution", institution);
                edu.put("degree", degree);
                edu.put("field", field);
                edu.put("startDate", startYear);
                edu.put("endDate", endYear);
                edu.put("description", snippet);
                edu.put("evidence", snippet);
                if (!isDuplicate(educationList, institution)) {
                    educationList.add(edu);
                }
            }
        }

        // 2. Scan for DEGREE_AT_INSTITUTION_PATTERN
        if (educationList.isEmpty()) {
            Matcher m2 = DEGREE_AT_INSTITUTION_PATTERN.matcher(text);
            while (m2.find() && educationList.size() < 4) {
                String degree = m2.group(1) != null ? m2.group(1).trim() : null;
                String field = m2.group(2) != null ? m2.group(2).trim() : null;
                String institution = m2.group(3) != null ? m2.group(3).trim() : null;

                if (isValidInstitution(institution)) {
                    String snippet = RoleFieldExtractor.extractSentence(text, m2.start(), m2.end());
                    if (primaryQuote == null) {
                        primaryQuote = snippet;
                    }
                    Map<String, Object> edu = new LinkedHashMap<>();
                    edu.put("institution", institution);
                    edu.put("degree", degree);
                    edu.put("field", field);
                    edu.put("startDate", null);
                    edu.put("endDate", null);
                    edu.put("description", snippet);
                    edu.put("evidence", snippet);
                    if (!isDuplicate(educationList, institution)) {
                        educationList.add(edu);
                    }
                }
            }
        }

        if (educationList.isEmpty()) {
            return null;
        }

        try {
            String jsonValue = MAPPER.writeValueAsString(educationList);
            SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
            SourceReliability rel = SourceTypeClassifier.determineReliability(type);
            ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

            return new EvidenceTuple(
                    jsonValue,
                    doc.url(),
                    primaryQuote != null ? primaryQuote : "Education record extracted",
                    tier,
                    List.of(doc.url()),
                    false,
                    null,
                    type.name(),
                    "FIELD_SPECIFIC_EDU_EXTRACTOR"
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isValidInstitution(String institution) {
        if (institution == null || institution.isBlank() || institution.length() < 2) return false;
        String lower = institution.toLowerCase(Locale.ROOT);
        return !lower.equals("a") && !lower.equals("the") && !lower.equals("an") && !lower.equals("high school");
    }

    private boolean isDuplicate(List<Map<String, Object>> list, String institution) {
        return list.stream().anyMatch(e ->
                institution.equalsIgnoreCase(String.valueOf(e.get("institution")))
        );
    }
}
