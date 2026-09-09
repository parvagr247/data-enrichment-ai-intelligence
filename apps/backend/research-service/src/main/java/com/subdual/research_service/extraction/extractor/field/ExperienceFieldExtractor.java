package com.subdual.research_service.extraction.extractor.field;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
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
 * Structured field extractor for professional work experience and position history.
 */
@Component
public class ExperienceFieldExtractor implements FieldExtractor {

    // Matches: "Staff Engineer at Stripe (2021 - Present)" or "Software Engineer at Google from 2018 to 2021"
    private static final Pattern EXP_DATE_PATTERN = Pattern.compile(
            "(?i)([A-Za-z0-9\\s-]{3,40})\\s+(?:at|with|@)\\s+([A-Za-z0-9&,\\s.-]{2,50}?)(?:\\s*[-–•|,]?\\s*(?:from\\s+)?(?:in\\s+)?\\(?((?:19|20)\\d{2})\\s*(?:-|–|to|until)\\s*(Present|current|(?:19|20)\\d{2})\\)?)?(?=[\\n\\r.;]|$)"
    );

    // Matches: "worked as a Software Engineer at Microsoft" or "is a Principal Architect at Red Hat"
    private static final Pattern VERB_ROLE_PATTERN = Pattern.compile(
            "(?i)(?:is\\s+(?:a|an)|works\\s+as\\s+(?:a|an)|worked\\s+as\\s+(?:a|an)|served\\s+as\\s+(?:a|an))\\s+([A-Za-z0-9\\s-]{3,40})\\s+(?:at|with)\\s+([A-Za-z0-9&,\\s.-]{2,50}?)(?=[\\n\\r.,;]|$)"
    );

    @Override
    public String fieldKey() {
        return "experience";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("experience") || lower.equals("workexperience") || lower.equals("work_experience")
                || lower.equals("currentexperience") || lower.equals("positions") || lower.equals("career") || lower.equals("jobs");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        List<Map<String, Object>> experiences = new ArrayList<>();
        String primaryQuote = null;

        // 1. Scan for role, organization, and date range patterns
        Matcher m1 = EXP_DATE_PATTERN.matcher(text);
        while (m1.find() && experiences.size() < 6) {
            String role = m1.group(1) != null ? m1.group(1).trim() : null;
            String org = m1.group(2) != null ? m1.group(2).trim() : null;
            String startDate = m1.group(3) != null ? m1.group(3).trim() : null;
            String endDate = m1.group(4) != null ? m1.group(4).trim() : null;

            if (isValidPosition(role, org)) {
                String snippet = RoleFieldExtractor.extractSentence(text, m1.start(), m1.end());
                if (primaryQuote == null) {
                    primaryQuote = snippet;
                }
                Map<String, Object> exp = new LinkedHashMap<>();
                exp.put("role", role);
                exp.put("organization", org);
                exp.put("startDate", startDate);
                exp.put("endDate", "present".equalsIgnoreCase(endDate) || "current".equalsIgnoreCase(endDate) ? null : endDate);
                exp.put("location", null);
                exp.put("description", snippet);
                exp.put("evidence", snippet);
                if (!isDuplicate(experiences, role, org)) {
                    experiences.add(exp);
                }
            }
        }

        // 2. Scan for verb-based role statements ("works as ... at ...")
        if (experiences.isEmpty()) {
            Matcher m2 = VERB_ROLE_PATTERN.matcher(text);
            while (m2.find() && experiences.size() < 3) {
                String role = m2.group(1).trim();
                String org = m2.group(2).trim();
                if (isValidPosition(role, org)) {
                    String snippet = RoleFieldExtractor.extractSentence(text, m2.start(), m2.end());
                    if (primaryQuote == null) {
                        primaryQuote = snippet;
                    }
                    Map<String, Object> exp = new LinkedHashMap<>();
                    exp.put("role", role);
                    exp.put("organization", org);
                    exp.put("startDate", null);
                    exp.put("endDate", null);
                    exp.put("location", null);
                    exp.put("description", snippet);
                    exp.put("evidence", snippet);
                    if (!isDuplicate(experiences, role, org)) {
                        experiences.add(exp);
                    }
                }
            }
        }

        // 3. Fall back to input target context if available and empty
        if (experiences.isEmpty() && target != null && target.organization() != null && !target.organization().isBlank()) {
            String role = target.role() != null && !target.role().isBlank() ? target.role().trim() : "Member";
            String org = target.organization().trim();
            Map<String, Object> exp = new LinkedHashMap<>();
            exp.put("role", role);
            exp.put("organization", org);
            exp.put("startDate", null);
            exp.put("endDate", null);
            exp.put("location", null);
            exp.put("description", "Current professional affiliation");
            exp.put("evidence", "Target metadata");
            experiences.add(exp);
            primaryQuote = role + " at " + org;
        }

        if (experiences.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < experiences.size(); i++) {
            Map<String, Object> exp = experiences.get(i);
            if (i > 0) sb.append(" | ");
            sb.append(exp.get("role")).append(" at ").append(exp.get("organization"));
            String start = (String) exp.get("startDate");
            String end = (String) exp.get("endDate");
            if (start != null || end != null) {
                sb.append(" (").append(start != null ? start : "?").append(" - ").append(end != null ? end : "Present").append(")");
            }
        }
        String formattedValue = sb.toString();

        SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
        SourceReliability rel = SourceTypeClassifier.determineReliability(type);
        ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

        return new EvidenceTuple(
                formattedValue,
                doc.url(),
                primaryQuote != null ? primaryQuote : "Document experience section",
                tier,
                List.of(doc.url()),
                false,
                null,
                type.name(),
                "STRUCTURED_EXPERIENCE_EXTRACTOR"
        );
    }

    private boolean isValidPosition(String role, String org) {
        if (role == null || org == null) return false;
        if (role.length() < 2 || org.length() < 2) return false;
        if (role.equalsIgnoreCase("home") || role.equalsIgnoreCase("about") || role.equalsIgnoreCase("contact")) return false;
        if (org.equalsIgnoreCase("home") || org.equalsIgnoreCase("linkedin") || org.equalsIgnoreCase("twitter")) return false;
        return true;
    }

    private boolean isDuplicate(List<Map<String, Object>> list, String role, String org) {
        return list.stream().anyMatch(e ->
                role.equalsIgnoreCase(String.valueOf(e.get("role"))) &&
                org.equalsIgnoreCase(String.valueOf(e.get("organization")))
        );
    }
}
