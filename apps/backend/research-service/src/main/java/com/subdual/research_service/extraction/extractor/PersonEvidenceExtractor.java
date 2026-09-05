package com.subdual.research_service.extraction.extractor;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.extraction.support.EvidenceMerger;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts attributes specific to PERSON entities using pattern heuristics and metadata corroboration.
 */
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

    @Autowired
    public PersonEvidenceExtractor(EvidenceMerger evidenceMerger) {
        this.evidenceMerger = evidenceMerger != null ? evidenceMerger : new EvidenceMerger();
    }

    public PersonEvidenceExtractor() {
        this(new EvidenceMerger());
    }

    public void extractAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(
                    target.displayName(),
                    target.canonicalUrl() != null ? target.canonicalUrl() : "",
                    "Target display name",
                    ConfidenceTier.HIGH
            ));
        }

        for (ExtractedDocument doc : documents) {
            if (!CommonEvidenceExtractor.isMatchedDocument(doc, resolutions)) {
                continue;
            }
            String text = doc.cleanText();
            if (text == null || text.isBlank()) {
                continue;
            }

            Matcher roleAtCompany = ROLE_AT_COMPANY_PATTERN.matcher(text);
            if (roleAtCompany.find()) {
                String role = roleAtCompany.group(1).trim();
                String company = roleAtCompany.group(2).trim();
                evidenceMerger.mergeAttribute(attributes, "role", role, doc.url(), "Pattern match: \"" + roleAtCompany.group(0) + "\"", ConfidenceTier.HIGH);
                evidenceMerger.mergeAttribute(attributes, "current_organization", company, doc.url(), "Pattern match: \"" + roleAtCompany.group(0) + "\"", ConfidenceTier.HIGH);
            }

            Matcher roleLabel = ROLE_LABEL_PATTERN.matcher(text);
            if (roleLabel.find()) {
                String role = roleLabel.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "role", role, doc.url(), "Role label: \"" + roleLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            }

            Matcher compLabel = COMPANY_LABEL_PATTERN.matcher(text);
            if (compLabel.find()) {
                String company = compLabel.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "current_organization", company, doc.url(), "Company label: \"" + compLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            }

            Matcher locLabel = LOCATION_LABEL_PATTERN.matcher(text);
            if (locLabel.find()) {
                String location = locLabel.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "location", location, doc.url(), "Location label: \"" + locLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            } else {
                Matcher locBased = LOCATION_BASED_IN_PATTERN.matcher(text);
                if (locBased.find()) {
                    String location = locBased.group(1).trim();
                    evidenceMerger.mergeAttribute(attributes, "location", location, doc.url(), "Location pattern: \"" + locBased.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
                }
            }

            Matcher eduLabel = EDUCATION_LABEL_PATTERN.matcher(text);
            if (eduLabel.find()) {
                String edu = eduLabel.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "education", edu, doc.url(), "Education label: \"" + eduLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            } else {
                Matcher eduGrad = EDUCATION_GRADUATED_PATTERN.matcher(text);
                if (eduGrad.find()) {
                    String edu = eduGrad.group(1).trim();
                    evidenceMerger.mergeAttribute(attributes, "education", edu, doc.url(), "Education pattern: \"" + eduGrad.group(0).trim() + "\"", ConfidenceTier.HIGH);
                }
            }

            Matcher skillsMatch = SKILLS_LABEL_PATTERN.matcher(text);
            if (skillsMatch.find()) {
                String skills = skillsMatch.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "skills", skills, doc.url(), "Skills label: \"" + skillsMatch.group(0).trim() + "\"", ConfidenceTier.HIGH);
            }

            Matcher expMatch = EXPERIENCE_LABEL_PATTERN.matcher(text);
            if (expMatch.find()) {
                String exp = expMatch.group(1).trim();
                evidenceMerger.mergeAttribute(attributes, "experience", exp, doc.url(), "Experience label: \"" + expMatch.group(0).trim() + "\"", ConfidenceTier.HIGH);
            }

            if (target.metadata() != null) {
                target.metadata().forEach((k, v) -> {
                    if (v != null) {
                        String valStr = v.toString().trim();
                        if (valStr.length() >= 3 && text.toLowerCase(Locale.ROOT).contains(valStr.toLowerCase(Locale.ROOT))) {
                            String attrKey = normalizeMetadataKey(k);
                            if (!attributes.containsKey(attrKey)) {
                                evidenceMerger.mergeAttribute(attributes, attrKey, valStr, doc.url(), "Corroborated by document text: \"" + valStr + "\"", ConfidenceTier.HIGH);
                            }
                        }
                    }
                });
            }
        }

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
