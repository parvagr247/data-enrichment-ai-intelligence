package com.subdual.research_service.extraction;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.ai.dto.AiExtractedFact;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EvidenceExtractor {

    private static final Pattern ROLE_AT_COMPANY_PATTERN = Pattern.compile(
            "(?i)(?:is\\s+(?:a|an)\\s+|works\\s+as\\s+(?:a|an)\\s+)([A-Za-z0-9\\s]{3,40})\\s+at\\s+([A-Za-z0-9\\s]{2,40})"
    );
    private static final Pattern ROLE_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Role|Title|Position):\\s*([A-Za-z0-9\\s-]{2,40})"
    );
    private static final Pattern COMPANY_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Company|Organization|Employer):\\s*([A-Za-z0-9\\s-]{2,40})"
    );
    private static final Pattern LOCATION_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Location|Based in):\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );
    private static final Pattern LOCATION_BASED_IN_PATTERN = Pattern.compile(
            "(?i)(?:based\\s+in|located\\s+in)\\s+([A-Za-z0-9\\s,.-]{2,35})(?:[\\n\\r.,]|$)"
    );
    private static final Pattern EDUCATION_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\n\\r•|])\\s*(?:Education|Degree|Alumni|Graduated from):\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );
    private static final Pattern EDUCATION_GRADUATED_PATTERN = Pattern.compile(
            "(?i)(?:graduated\\s+from|degree\\s+from|studied\\s+at)\\s+([A-Za-z0-9\\s]{2,30}?)(?:\\s+with|\\s+in|[\\n\\r.,]|$)"
    );
    private static final Pattern HQ_PATTERN = Pattern.compile(
            "(?i)(?:headquarters|headquartered in|based in):?\\s*([A-Za-z0-9\\s,.-]{2,40})"
    );

    private final AiExtractionClient aiExtractionClient;

    public EvidenceExtractor(AiExtractionClient aiExtractionClient) {
        this.aiExtractionClient = aiExtractionClient != null ? aiExtractionClient : new NoOpAiExtractionClient();
    }

    public Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> sources,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();

        if (target == null || documents == null || documents.isEmpty()) {
            return attributes;
        }

        putIfPresent(attributes, "description", extractDescription(target, documents, sources, resolutions));
        putIfPresent(attributes, "title", extractTitle(target, documents, sources, resolutions));
        putIfPresent(attributes, "site_name", extractSiteName(documents, resolutions));

        if (target.entityType() == EntityType.PERSON) {
            extractPersonAttributes(target, documents, attributes, resolutions);
        } else if (target.entityType() == EntityType.ORGANIZATION) {
            extractOrganizationAttributes(target, documents, attributes, resolutions);
        } else if (target.entityType() == EntityType.REPOSITORY) {
            putIfPresent(attributes, "repository", extractRepositoryInfo(target, documents));
            extractRepositoryAttributes(target, documents, attributes, resolutions);
        }

        if (aiExtractionClient != null) {
            enrichWithAiExtraction(target, documents, resolutions, attributes);
        }

        if (target.targetFields() != null && !target.targetFields().isEmpty()) {
            filterToTargetFields(attributes, target.targetFields());
        }

        return attributes;
    }

    private void filterToTargetFields(Map<String, EvidenceTuple> attributes, List<String> targetFields) {
        java.util.Set<String> requested = targetFields.stream()
                .map(f -> f.toLowerCase(Locale.ROOT).trim())
                .collect(java.util.stream.Collectors.toSet());
        attributes.keySet().removeIf(k -> !requested.contains(k.toLowerCase(Locale.ROOT)) && !"name".equals(k));
    }

    private void extractPersonAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(target.displayName(), target.canonicalUrl() != null ? target.canonicalUrl() : "", "Target display name", ConfidenceTier.HIGH));
        }

        for (ExtractedDocument doc : documents) {
            if (!isMatchedDocument(doc, resolutions)) {
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
                mergeAttribute(attributes, "role", role, doc.url(), "Pattern match: \"" + roleAtCompany.group(0) + "\"", ConfidenceTier.HIGH);
                mergeAttribute(attributes, "current_organization", company, doc.url(), "Pattern match: \"" + roleAtCompany.group(0) + "\"", ConfidenceTier.HIGH);
            }

            Matcher roleLabel = ROLE_LABEL_PATTERN.matcher(text);
            if (roleLabel.find()) {
                String role = roleLabel.group(1).trim();
                mergeAttribute(attributes, "role", role, doc.url(), "Role label: \"" + roleLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            }

            Matcher compLabel = COMPANY_LABEL_PATTERN.matcher(text);
            if (compLabel.find()) {
                String company = compLabel.group(1).trim();
                mergeAttribute(attributes, "current_organization", company, doc.url(), "Company label: \"" + compLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            }

            Matcher locLabel = LOCATION_LABEL_PATTERN.matcher(text);
            if (locLabel.find()) {
                String location = locLabel.group(1).trim();
                mergeAttribute(attributes, "location", location, doc.url(), "Location label: \"" + locLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            } else {
                Matcher locBased = LOCATION_BASED_IN_PATTERN.matcher(text);
                if (locBased.find()) {
                    String location = locBased.group(1).trim();
                    mergeAttribute(attributes, "location", location, doc.url(), "Location pattern: \"" + locBased.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
                }
            }

            Matcher eduLabel = EDUCATION_LABEL_PATTERN.matcher(text);
            if (eduLabel.find()) {
                String edu = eduLabel.group(1).trim();
                mergeAttribute(attributes, "education", edu, doc.url(), "Education label: \"" + eduLabel.group(0).trim() + "\"", ConfidenceTier.HIGH);
            } else {
                Matcher eduGrad = EDUCATION_GRADUATED_PATTERN.matcher(text);
                if (eduGrad.find()) {
                    String edu = eduGrad.group(1).trim();
                    mergeAttribute(attributes, "education", edu, doc.url(), "Education pattern: \"" + eduGrad.group(0).trim() + "\"", ConfidenceTier.HIGH);
                }
            }

            if (target.metadata() != null) {
                target.metadata().forEach((k, v) -> {
                    if (v != null) {
                        String valStr = v.toString().trim();
                        if (valStr.length() >= 3 && text.toLowerCase(Locale.ROOT).contains(valStr.toLowerCase(Locale.ROOT))) {
                            String attrKey = normalizeMetadataKey(k);
                            if (!attributes.containsKey(attrKey)) {
                                mergeAttribute(attributes, attrKey, valStr, doc.url(), "Corroborated by document text: \"" + valStr + "\"", ConfidenceTier.HIGH);
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

    private void extractOrganizationAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(target.displayName(), target.canonicalUrl() != null ? target.canonicalUrl() : "", "Display name of organization", ConfidenceTier.HIGH));
        }
        if (target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) {
            attributes.putIfAbsent("website", new EvidenceTuple(target.canonicalUrl(), target.canonicalUrl(), "Canonical organization website", ConfidenceTier.HIGH));
        }

        for (ExtractedDocument doc : documents) {
            if (!isMatchedDocument(doc, resolutions)) continue;
            String text = doc.cleanText();
            if (text == null || text.isBlank()) continue;

            Matcher hqMatcher = HQ_PATTERN.matcher(text);
            if (hqMatcher.find()) {
                String hq = hqMatcher.group(1).trim();
                mergeAttribute(attributes, "headquarters", hq, doc.url(), "Headquarters pattern: \"" + hqMatcher.group(0).trim() + "\"", ConfidenceTier.MEDIUM);
            }
        }
    }

    private void extractRepositoryAttributes(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (target.displayName() != null && !target.displayName().isBlank()) {
            attributes.putIfAbsent("name", new EvidenceTuple(target.displayName(), target.canonicalUrl() != null ? target.canonicalUrl() : "", "Repository name", ConfidenceTier.HIGH));
        }

        for (ExtractedDocument doc : documents) {
            if (!isMatchedDocument(doc, resolutions)) continue;
            String text = doc.cleanText();
            if (text == null || text.isBlank()) continue;

            List<String> techs = detectTechnologies(text);
            if (!techs.isEmpty() && !attributes.containsKey("technologies")) {
                String joined = String.join(", ", techs);
                mergeAttribute(attributes, "technologies", joined, doc.url(), "Technologies mentioned in documentation: " + joined, ConfidenceTier.MEDIUM);
            }
        }
    }

    private List<String> detectTechnologies(String text) {
        List<String> detected = new ArrayList<>();
        String[] keywords = {"Java", "Kotlin", "TypeScript", "JavaScript", "Python", "Go", "Rust", "Spring Boot", "Docker", "Kubernetes"};
        for (String kw : keywords) {
            if (text.contains(kw)) {
                detected.add(kw);
            }
        }
        return detected;
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

    private void putIfPresent(Map<String, EvidenceTuple> attributes, String key, EvidenceTuple evidence) {
        if (evidence != null) {
            attributes.put(key, evidence);
        }
    }

    private void enrichWithAiExtraction(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions,
            Map<String, EvidenceTuple> attributes
    ) {
        for (ExtractedDocument doc : documents) {
            if (!isMatchedDocument(doc, resolutions)) {
                continue;
            }

            List<String> targetFields = (target.targetFields() != null && !target.targetFields().isEmpty())
                    ? target.targetFields()
                    : List.of("role", "organization", "description", "summary", "headquarters", "technologies");

            Map<String, AiExtractedFact> facts = aiExtractionClient.extractFacts(
                    target.displayName(),
                    target.entityType() != null ? target.entityType().name() : "OTHER",
                    doc.url(),
                    doc.cleanText(),
                    targetFields
            );

            mergeAiFacts(facts, doc.url(), doc.cleanText(), attributes);
        }
    }

    private boolean isMatchedDocument(ExtractedDocument doc, Map<String, EntityResolver.ResolutionResult> resolutions) {
        EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
        return res.matched() && doc.cleanText() != null && !doc.cleanText().isBlank();
    }

    private void mergeAiFacts(Map<String, AiExtractedFact> facts, String sourceUrl, String docText, Map<String, EvidenceTuple> attributes) {
        if (facts == null) {
            return;
        }

        facts.forEach((factKey, fact) -> {
            if (fact != null && fact.value() != null && !fact.value().isBlank()) {
                if (!isFactGroundedInSource(fact, docText)) {
                    return;
                }
                ConfidenceTier tier = resolveAiConfidenceTier(fact.confidenceScore());
                String snippet = resolveAiSnippet(fact.exactQuote());
                mergeAttribute(attributes, factKey, fact.value(), sourceUrl, snippet, tier);
            }
        });
    }

    private boolean isFactGroundedInSource(AiExtractedFact fact, String docText) {
        if (docText == null || docText.isBlank()) {
            return false;
        }
        String lowerDoc = docText.toLowerCase(Locale.ROOT);
        if (fact.exactQuote() != null && !fact.exactQuote().isBlank()) {
            String quote = fact.exactQuote().trim().toLowerCase(Locale.ROOT);
            if (lowerDoc.contains(quote)) {
                return true;
            }
        }
        String val = fact.value().trim().toLowerCase(Locale.ROOT);
        return lowerDoc.contains(val);
    }

    private ConfidenceTier resolveAiConfidenceTier(double score) {
        if (score >= 0.8) return ConfidenceTier.HIGH;
        if (score >= 0.5) return ConfidenceTier.MEDIUM;
        return ConfidenceTier.LOW;
    }

    private String resolveAiSnippet(String exactQuote) {
        return (exactQuote != null && !exactQuote.isBlank())
                ? "AI Quote: \"" + exactQuote + "\""
                : "AI Structured Extraction";
    }

    private void mergeAttribute(
            Map<String, EvidenceTuple> attributes,
            String key,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier
    ) {
        if (value == null || value.isBlank()) {
            return;
        }

        EvidenceTuple existing = attributes.get(key);
        if (existing == null) {
            attributes.put(key, new EvidenceTuple(value, sourceUrl, snippet, tier));
            return;
        }

        List<String> sources = buildCorroboratingSources(existing, sourceUrl);
        if (isAgreement(existing.value(), value)) {
            attributes.put(key, corroborateAgreement(existing, value, snippet, sources));
        } else {
            attributes.put(key, resolveDisagreement(existing, value, sourceUrl, snippet, tier, sources));
        }
    }

    private List<String> buildCorroboratingSources(EvidenceTuple existing, String sourceUrl) {
        List<String> sources = new ArrayList<>(existing.corroboratingSources() != null ? existing.corroboratingSources() : List.of());
        if (sourceUrl != null && !sources.contains(sourceUrl)) {
            sources.add(sourceUrl);
        }
        return sources;
    }

    private EvidenceTuple corroborateAgreement(
            EvidenceTuple existing,
            String value,
            String snippet,
            List<String> sources
    ) {
        ConfidenceTier current = existing.confidence() != null ? existing.confidence() : ConfidenceTier.LOW;
        ConfidenceTier boostedTier = switch (current) {
            case UNKNOWN, LOW -> ConfidenceTier.MEDIUM;
            case MEDIUM, HIGH -> ConfidenceTier.HIGH;
        };

        String combinedSnippet = existing.evidenceSnippet() != null ? existing.evidenceSnippet() : snippet;
        if (snippet != null && !combinedSnippet.contains(snippet)) {
            combinedSnippet += " | Corroborating: " + snippet;
        }

        return new EvidenceTuple(
                existing.value(),
                existing.sourceUrl(),
                combinedSnippet,
                boostedTier,
                sources,
                existing.conflictDetected()
        );
    }

    private EvidenceTuple resolveDisagreement(
            EvidenceTuple existing,
            String value,
            String sourceUrl,
            String snippet,
            ConfidenceTier tier,
            List<String> sources
    ) {
        int comp = compareConfidence(tier, existing.confidence());
        if (comp > 0) {
            String conflictSnippet = snippet + " (Alternative '" + existing.value() + "' found in " + existing.sourceUrl() + ")";
            ConfidenceTier resolvedTier = tier == ConfidenceTier.HIGH ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW;
            return new EvidenceTuple(value, sourceUrl, conflictSnippet, resolvedTier, sources, true);
        }

        String conflictSnippet = existing.evidenceSnippet() + " (Conflict: alternative '" + value + "' reported in " + sourceUrl + ")";
        ConfidenceTier resolvedTier = (comp == 0 && existing.confidence() == ConfidenceTier.HIGH)
                ? ConfidenceTier.MEDIUM
                : existing.confidence();
        return new EvidenceTuple(existing.value(), existing.sourceUrl(), conflictSnippet, resolvedTier, sources, true);
    }

    private boolean isAgreement(String v1, String v2) {
        if (v1 == null || v2 == null) return false;
        String s1 = v1.trim().toLowerCase(Locale.ROOT);
        String s2 = v2.trim().toLowerCase(Locale.ROOT);
        return s1.equals(s2) || (s1.length() > 10 && s2.length() > 10 && (s1.contains(s2) || s2.contains(s1)));
    }

    private int compareConfidence(ConfidenceTier t1, ConfidenceTier t2) {
        if (t1 == t2) return 0;
        if (t1 == ConfidenceTier.HIGH) return 1;
        if (t2 == ConfidenceTier.HIGH) return -1;
        if (t1 == ConfidenceTier.MEDIUM) return 1;
        return -1;
    }

    private EvidenceTuple extractDescription(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        EvidenceTuple metaDesc = extractMetaDescription(documents, resolutions);
        if (metaDesc != null) {
            return metaDesc;
        }

        EvidenceTuple excerpt = extractParagraphExcerpt(documents, resolutions);
        if (excerpt != null) {
            return excerpt;
        }

        return extractSearchSnippet(sources, resolutions);
    }

    private EvidenceTuple extractMetaDescription(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            if (doc.metaDescription() != null && !doc.metaDescription().isBlank()) {
                EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
                if (res.matched()) {
                    ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH
                            ? ConfidenceTier.HIGH
                            : (res.confidence() == ConfidenceTier.MEDIUM ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW);
                    return new EvidenceTuple(doc.metaDescription(), doc.url(), "Meta description: \"" + doc.metaDescription() + "\"", tier);
                }
            }
        }
        return null;
    }

    private EvidenceTuple extractParagraphExcerpt(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
            if (res.matched() && doc.cleanText() != null && doc.cleanText().length() > 40) {
                String text = doc.cleanText();
                int endIdx = Math.min(text.length(), 200);
                int periodIdx = text.indexOf('.', 40);
                if (periodIdx > 0 && periodIdx <= endIdx) {
                    endIdx = periodIdx + 1;
                }
                String excerpt = text.substring(0, endIdx).trim();
                ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH ? ConfidenceTier.MEDIUM : ConfidenceTier.LOW;
                return new EvidenceTuple(excerpt, doc.url(), "Excerpt: \"" + excerpt + "\"", tier);
            }
        }
        return null;
    }

    private EvidenceTuple extractSearchSnippet(
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (sources == null) {
            return null;
        }
        for (ResearchSource src : sources) {
            EntityResolver.ResolutionResult res = getResolution(src.url(), resolutions);
            if (res.matched() && src.snippet() != null && !src.snippet().isBlank()) {
                return new EvidenceTuple(
                        src.snippet(),
                        src.url(),
                        "Search snippet: \"" + src.snippet() + "\"",
                        ConfidenceTier.LOW
                );
            }
        }
        return null;
    }

    private EvidenceTuple extractTitle(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        EvidenceTuple docTitle = extractDocumentTitle(documents, resolutions);
        if (docTitle != null) {
            return docTitle;
        }
        return extractSourceTitle(sources, resolutions);
    }

    private EvidenceTuple extractDocumentTitle(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            if (doc.title() != null && !doc.title().isBlank()) {
                EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
                if (res.matched()) {
                    ConfidenceTier tier = res.confidence() == ConfidenceTier.HIGH
                            ? ConfidenceTier.HIGH
                            : ConfidenceTier.MEDIUM;
                    return new EvidenceTuple(doc.title(), doc.url(), "Page title: \"" + doc.title() + "\"", tier);
                }
            }
        }
        return null;
    }

    private EvidenceTuple extractSourceTitle(
            List<ResearchSource> sources,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        if (sources == null) {
            return null;
        }
        for (ResearchSource src : sources) {
            EntityResolver.ResolutionResult res = getResolution(src.url(), resolutions);
            if (res.matched() && src.title() != null && !src.title().isBlank()) {
                return new EvidenceTuple(src.title(), src.url(), "Source title: \"" + src.title() + "\"", ConfidenceTier.LOW);
            }
        }
        return null;
    }

    private EvidenceTuple extractSiteName(
            List<ExtractedDocument> documents,
            Map<String, EntityResolver.ResolutionResult> resolutions
    ) {
        for (ExtractedDocument doc : documents) {
            if (doc.siteName() != null && !doc.siteName().isBlank()) {
                EntityResolver.ResolutionResult res = getResolution(doc.url(), resolutions);
                if (res.matched()) {
                    return new EvidenceTuple(
                            doc.siteName(),
                            doc.url(),
                            "OpenGraph site_name: \"" + doc.siteName() + "\"",
                            ConfidenceTier.MEDIUM
                    );
                }
            }
        }
        return null;
    }

    private EvidenceTuple extractRepositoryInfo(ResearchTarget target, List<ExtractedDocument> documents) {
        try {
            URI uri = URI.create(target.canonicalUrl());
            if (uri.getHost() != null && uri.getHost().contains("github.com")) {
                String path = uri.getPath() != null ? uri.getPath().replaceAll("^/|/$", "") : "";
                if (path.contains("/")) {
                    return new EvidenceTuple(
                            path,
                            target.canonicalUrl(),
                            "Parsed repository coordinates from canonical target URL",
                            ConfidenceTier.HIGH
                    );
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private EntityResolver.ResolutionResult getResolution(String url, Map<String, EntityResolver.ResolutionResult> resolutions) {
        return resolutions != null
                ? resolutions.getOrDefault(url, new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown"))
                : new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Unknown");
    }
}
