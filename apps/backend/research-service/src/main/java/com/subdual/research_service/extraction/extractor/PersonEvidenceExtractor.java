package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PersonEvidenceExtractor {

    private static final Pattern ROLE_AT_COMPANY_PATTERN = Pattern.compile(
            "(?i)(?:is\\s+(?:a|an)\\s+|works\\s+as\\s+(?:a|an)\\s+)([A-Za-z0-9\\s]{3,40})\\s+at\\s+([A-Za-z0-9\\s]{2,60}?)(?=\\s+(?:in|based in)\\b|[\\n\\r.,(]|$)"
    );
    private static final Pattern ROLE_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|.]|\\b)\\s*(?:Role|Title|Position):\\s*([A-Za-z0-9\\s-]{2,40}?)(?:[\\n\\r.]|$)"
    );
    private static final Pattern COMPANY_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|.]|\\b)\\s*(?:Company|Organization|Employer):\\s*([A-Za-z0-9\\s-]{2,60}?)(?:[\\n\\r.]|$)"
    );
    private static final Pattern LOCATION_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|.]|\\b)\\s*(?:Location|Based in):\\s*([A-Za-z0-9\\s,-]{2,40}?)(?:[\\n\\r.]|$)"
    );
    private static final Pattern LOCATION_BASED_IN_PATTERN = Pattern.compile(
            "(?i)(?:based\\s+in|located\\s+in)\\s+([A-Za-z0-9\\s,.-]{2,35})(?:[\\n\\r.,]|$)"
    );
    private static final Pattern EDUCATION_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|.]|\\b)\\s*(?:Education|Degree|Alumni|Graduated from):\\s*([A-Za-z0-9\\s,.-]{2,60}?)(?:[\\n\\r.]|$)"
    );
    private static final Pattern EDUCATION_GRADUATED_PATTERN = Pattern.compile(
            "(?i)(?:graduated\\s+from|degree\\s+from|studied\\s+at)\\s+([A-Za-z0-9\\s]{2,60}?)(?:\\s+with|\\s+in|[\\n\\r.,]|$)"
    );
    private static final Pattern SKILLS_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|.]|\\b)\\s*(?:Skills|Expertise|Proficiencies):\\s*([A-Za-z0-9\\s,./+ -]{2,60}?)(?:[\\n\\r.]|$)"
    );
    private static final Pattern EXPERIENCE_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|.]|\\b)\\s*(?:Experience|Work Experience):\\s*([A-Za-z0-9\\s,.-]{2,50}?)(?:[\\n\\r.]|$)"
    );

    private final EvidenceMerger evidenceMerger;

    public PersonEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public void extractAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        populateNameIfPresent(target, attributes);

        for (ExtractedDocument doc : documents) {
            if (shouldProcessDocument(doc, resolutions)) {
                extractFromDocument(target, doc, attributes);
            }
        }

        populateSummaryFromDescription(attributes);
    }

    private void populateNameIfPresent(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        if (target != null && target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Target display name",
                    ConfidenceTier.HIGH
            ));
        }
    }

    private boolean shouldProcessDocument(ExtractedDocument doc, Map<String, EntityResolver.ResolutionResult> resolutions) {
        return CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)
                && doc.cleanText() != null
                && !doc.cleanText().isBlank();
    }

    private void extractFromDocument(ResearchTarget target, ExtractedDocument doc, Map<String, EvidenceTuple> attributes) {
        String text = doc.cleanText();
        String url = doc.url();
        String method = doc.extractionMethod();

        extractRoleAndCompany(text, url, method, attributes);
        extractRoleLabel(text, url, method, attributes);
        extractCompanyLabel(text, url, method, attributes);
        extractLocation(text, url, method, attributes);
        extractEducation(text, url, method, attributes);
        extractSkills(text, url, method, attributes);
        extractExperience(text, url, method, attributes);
        extractCorroboratedMetadata(target, text, url, method, attributes);
    }

    private void extractRoleAndCompany(String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = ROLE_AT_COMPANY_PATTERN.matcher(text);
        if (matcher.find()) {
            String role = matcher.group(1).trim();
            String company = matcher.group(2).trim();
            evidenceMerger.mergeAttribute(attributes, "role", role, url, "Pattern match: \"" + matcher.group(0) + "\"", ConfidenceTier.HIGH, null, method);
            evidenceMerger.mergeAttribute(attributes, "current_organization", company, url, "Pattern match: \"" + matcher.group(0) + "\"", ConfidenceTier.HIGH, null, method);
        }
    }

    private void extractRoleLabel(String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = ROLE_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String role = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "role", role, url, "Role label: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.HIGH, null, method);
        }
    }

    private void extractCompanyLabel(String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = COMPANY_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String company = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "current_organization", company, url, "Company label: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.HIGH, null, method);
        }
    }

    private void extractLocation(String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        Matcher labelMatcher = LOCATION_LABEL_PATTERN.matcher(text);
        if (labelMatcher.find()) {
            String location = labelMatcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "location", location, url, "Location label: \"" + labelMatcher.group(0).trim() + "\"", ConfidenceTier.HIGH, null, method);
            return;
        }

        Matcher basedMatcher = LOCATION_BASED_IN_PATTERN.matcher(text);
        if (basedMatcher.find()) {
            String location = basedMatcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "location", location, url, "Location pattern: \"" + basedMatcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM, null, method);
        }
    }

    private void extractEducation(String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        Matcher labelMatcher = EDUCATION_LABEL_PATTERN.matcher(text);
        if (labelMatcher.find()) {
            String edu = labelMatcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "education", edu, url, "Education label: \"" + labelMatcher.group(0).trim() + "\"", ConfidenceTier.HIGH, null, method);
            return;
        }

        Matcher gradMatcher = EDUCATION_GRADUATED_PATTERN.matcher(text);
        if (gradMatcher.find()) {
            String edu = gradMatcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "education", edu, url, "Education pattern: \"" + gradMatcher.group(0).trim() + "\"", ConfidenceTier.HIGH, null, method);
        }
    }

    private void extractSkills(String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = SKILLS_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String skills = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "skills", skills, url, "Skills label: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.HIGH, null, method);
        }
    }

    private void extractExperience(String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        Matcher matcher = EXPERIENCE_LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            String exp = matcher.group(1).trim();
            evidenceMerger.mergeAttribute(attributes, "experience", exp, url, "Experience label: \"" + matcher.group(0).trim() + "\"", ConfidenceTier.HIGH, null, method);
        }
    }

    private void extractCorroboratedMetadata(ResearchTarget target, String text, String url, String method, Map<String, EvidenceTuple> attributes) {
        if (target == null || target.metadata() == null) {
            return;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        target.metadata().forEach((k, v) -> {
            if (v != null) {
                String valStr = v.toString().trim();
                if (valStr.length() >= 3 && lowerText.contains(valStr.toLowerCase(Locale.ROOT))) {
                    String attrKey = normalizeMetadataKey(k);
                    if (!attributes.containsKey(attrKey)) {
                        evidenceMerger.mergeAttribute(attributes, attrKey, valStr, url, "Corroborated by document text: \"" + valStr + "\"", ConfidenceTier.HIGH, null, method);
                    }
                }
            }
        });
    }

    private void populateSummaryFromDescription(Map<String, EvidenceTuple> attributes) {
        if (attributes.containsKey("description") && !attributes.containsKey("summary")) {
            EvidenceTuple desc = attributes.get("description");
            attributes.put("summary", new EvidenceTuple(desc.value(), desc.sourceUrl(), desc.evidenceSnippet(), desc.confidence()));
        }
    }

    private String normalizeMetadataKey(String key) {
        if (key == null) return "attribute";
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.equals("company") || lower.equals("org") || lower.equals("organization")) {
            return "current_organization";
        }
        if (lower.equals("title") || lower.equals("position") || lower.equals("role")) {
            return "role";
        }
        return lower;
    }
}
