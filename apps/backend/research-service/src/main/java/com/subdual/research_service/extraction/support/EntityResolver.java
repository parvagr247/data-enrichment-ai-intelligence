package com.subdual.research_service.extraction.support;

import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class EntityResolver {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EntityResolver.class);

    private static final Set<String> MULTI_TENANT_HOSTS = Set.of(
            "linkedin.com", "github.com", "gitlab.com", "twitter.com", "x.com",
            "facebook.com", "instagram.com", "youtube.com", "medium.com", "wikipedia.org"
    );

    private static final Set<String> CONFLICTING_PROFESSIONS = Set.of(
            "dentist", "dentistry", "dental", "dds", "dmd",
            "actress", "actor", "filmography", "hollywood", "imdb",
            "realtor", "real estate", "broker",
            "physician", "pediatrician", "surgeon", "clinic"
    );

    public enum MatchStatus {
        MATCHED,
        AMBIGUOUS,
        NOT_MATCHED
    }

    public record ResolutionResult(
            ConfidenceTier confidence,
            MatchStatus status,
            boolean matched,
            String reason,
            double score,
            java.util.List<String> matchedSignals
    ) {
        public ResolutionResult(ConfidenceTier confidence, MatchStatus status, boolean matched, String reason) {
            this(confidence, status, matched, reason, confidenceToScore(confidence), java.util.List.of(reason));
        }

        public ResolutionResult(ConfidenceTier confidence, MatchStatus status, String reason) {
            this(confidence, status, status == MatchStatus.MATCHED, reason);
        }

        public ResolutionResult(ConfidenceTier confidence, boolean matched, String reason) {
            this(confidence, matched ? MatchStatus.MATCHED : MatchStatus.NOT_MATCHED, matched, reason);
        }

        private static double confidenceToScore(ConfidenceTier tier) {
            if (tier == null) return 0.0;
            return switch (tier) {
                case HIGH -> 0.95;
                case MEDIUM -> 0.70;
                case LOW -> 0.35;
                default -> 0.10;
            };
        }
    }

    public ResolutionResult resolve(ResearchTarget target, ExtractedDocument document) {
        ResolutionResult result = executeResolution(target, document);
        log.info("[Pipeline: ENTITY_RESOLUTION] Candidate='{}' Decision='{}' Reason='{}' Score={}",
                document != null ? document.url() : "null", result.status(), result.reason(), result.score());
        return result;
    }

    private ResolutionResult executeResolution(ResearchTarget target, ExtractedDocument document) {
        if (!isValidResolutionInput(target, document)) {
            return new ResolutionResult(ConfidenceTier.UNKNOWN, MatchStatus.NOT_MATCHED, false, "Missing target or document", 0.0, java.util.List.of("NO_INPUT"));
        }

        if (isAnchorUrl(document.url(), target)) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Primary anchor URL match", 0.98, java.util.List.of("ANCHOR_URL_MATCH: " + document.url()));
        }

        if (matchesNonMultiTenantHost(target.canonicalUrl(), document.url())) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Host match with target canonical URL", 0.95, java.util.List.of("HOST_MATCH"));
        }

        if (isConflictingEntity(target, document)) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "Rejected: Conflicting entity identity signals detected", 0.10, java.util.List.of("CONFLICTING_SIGNALS_REJECTED"));
        }

        String targetSlug = extractSlug(target.canonicalUrl() != null ? target.canonicalUrl() : target.rawUrl());

        ResolutionResult titleResult = checkTitleMatch(target, document, targetSlug);
        if (titleResult != null) {
            return titleResult;
        }

        if (matchesSlugOrUrl(targetSlug, target.canonicalUrl(), document)) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Identity handle or profile slug corroborated in source", 0.90, java.util.List.of("SLUG_MATCH: " + (targetSlug != null ? targetSlug : "")));
        }

        java.util.List<String> signals = new java.util.ArrayList<>();
        if (matchesMetadataSignals(target, document, signals)) {
            signals.add("CORROBORATING_METADATA");
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name and corroborating identity signals matched", 0.85, signals);
        }

        return resolveFallback(target, document);
    }

    private boolean isValidResolutionInput(ResearchTarget target, ExtractedDocument document) {
        return target != null && document != null && document.url() != null;
    }

    private ResolutionResult checkTitleMatch(ResearchTarget target, ExtractedDocument document, String targetSlug) {
        if (!hasDistinctDisplayName(target)) {
            return null;
        }

        String lowerName = target.displayName().toLowerCase(Locale.ROOT);
        String normName = NameNormalizer.normalize(target.displayName()).toLowerCase(Locale.ROOT);
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";

        boolean titleMatched = docTitle.contains(lowerName) || (!normName.isBlank() && docTitle.contains(normName));
        if (!titleMatched && !normName.isBlank() && !docTitle.isBlank()) {
            titleMatched = FuzzyMatcher.isFuzzyMatch(normName, docTitle, 0.85);
        }

        if (titleMatched) {
            if (isSingleTokenName(target) && !hasCorroboratingSignals(targetSlug, target, document)) {
                return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Ambiguous: First-name only match without corroborating identity signals", 0.30, java.util.List.of("AMBIGUOUS_FIRST_NAME_ONLY"));
            }
            if (isAnchoredPersonTarget(target) && !hasCorroboratingSignals(targetSlug, target, document)) {
                return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Rejected: Name-only match without corroborating identity signals", 0.35, java.util.List.of("REJECTED_UNNOTICED_SIGNALS"));
            }
            if (target.entityType() == EntityType.PERSON && !hasCorroboratingSignals(targetSlug, target, document)) {
                return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Ambiguous: Name-only match without corroborating identity signals", 0.35, java.util.List.of("AMBIGUOUS_PERSON_NO_CORROBORATION"));
            }
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name matched in document title", 0.90, java.util.List.of("TITLE_NAME_MATCH"));
        }
        return null;
    }

    private ResolutionResult resolveFallback(ResearchTarget target, ExtractedDocument document) {
        if (!hasDistinctDisplayName(target)) {
            return resolveWithoutDisplayName(target.canonicalUrl(), document.url());
        }

        if (isAnchoredPersonTarget(target) && hasAnchorOrMetadata(target)) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Rejected: Name-only match without corroborating identity signals");
        }

        return resolveByEntityName(target, document);
    }

    private boolean hasCorroboratingSignals(String targetSlug, ResearchTarget target, ExtractedDocument document) {
        return matchesSlugOrUrl(targetSlug, target.canonicalUrl(), document)
                || matchesMetadataSignals(target, document);
    }

    private boolean isAnchorUrl(String docUrl, ResearchTarget target) {
        if (target == null || docUrl == null) {
            return false;
        }
        if (target.canonicalUrl() != null && isSameUrl(docUrl, target.canonicalUrl())) {
            return true;
        }
        if (target.rawUrl() != null && isSameUrl(docUrl, target.rawUrl())) {
            return true;
        }
        return false;
    }

    private boolean isSameUrl(String u1, String u2) {
        return com.subdual.research_service.util.UrlNormalizer.isSameUrl(u1, u2);
    }

    private boolean matchesNonMultiTenantHost(String canonicalUrl, String docUrl) {
        if (canonicalUrl == null || docUrl == null) {
            return false;
        }
        try {
            URI targetUri = URI.create(canonicalUrl);
            URI docUri = URI.create(docUrl);
            String targetHost = extractHost(targetUri);
            String docHost = extractHost(docUri);

            if (targetHost.isBlank() || docHost.isBlank()) {
                return false;
            }
            if (MULTI_TENANT_HOSTS.stream().anyMatch(targetHost::endsWith)) {
                return false;
            }
            return targetHost.equalsIgnoreCase(docHost);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isConflictingEntity(ResearchTarget target, ExtractedDocument document) {
        if (hasConflictingProfileSlug(target, document)) {
            return true;
        }

        String lowerText = extractFullDocumentText(document);

        if (hasConflictingProfession(lowerText, target)) {
            return true;
        }

        return hasConflictingOrganization(lowerText, document.title(), target);
    }

    private String extractFullDocumentText(ExtractedDocument document) {
        String title = document.title() != null ? document.title() : "";
        String body = document.cleanText() != null ? document.cleanText() : "";
        return (title + " " + body).toLowerCase(Locale.ROOT);
    }

    private boolean hasConflictingProfession(String lowerText, ResearchTarget target) {
        boolean containsProfessionKeyword = CONFLICTING_PROFESSIONS.stream().anyMatch(lowerText::contains);
        if (!containsProfessionKeyword) {
            return false;
        }

        String targetContext = getTargetContext(target).toLowerCase(Locale.ROOT);
        for (String prof : CONFLICTING_PROFESSIONS) {
            if (lowerText.contains(prof) && targetContext.contains(prof)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasConflictingProfileSlug(ResearchTarget target, ExtractedDocument document) {
        if (target == null || document == null || document.url() == null) return false;
        String targetUrl = target.canonicalUrl() != null && !target.canonicalUrl().isBlank() ? target.canonicalUrl() : target.rawUrl();
        if (targetUrl == null || targetUrl.isBlank()) return false;

        String docUrl = document.url();
        String targetSlug = extractProfileSlug(targetUrl);
        String docSlug = extractProfileSlug(docUrl);

        if (targetSlug != null && docSlug != null) {
            String targetHost = extractHostFromUrl(targetUrl);
            String docHost = extractHostFromUrl(docUrl);
            if (!targetHost.isBlank() && targetHost.equalsIgnoreCase(docHost)) {
                return !targetSlug.equalsIgnoreCase(docSlug);
            }
        }
        return false;
    }

    private String extractProfileSlug(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String clean = com.subdual.research_service.util.UrlNormalizer.unwrapLink(url);
            URI uri = URI.create(clean);
            String host = uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
            String path = uri.getPath();
            if (path == null) return null;

            if (host.contains("linkedin.com") && path.contains("/in/")) {
                int idx = path.indexOf("/in/");
                String sub = path.substring(idx + 4).replaceAll("^/+|/+$", "");
                int slash = sub.indexOf('/');
                return slash > 0 ? sub.substring(0, slash) : sub;
            }
            if (host.contains("github.com") && !path.isBlank()) {
                String sub = path.replaceAll("^/+|/+$", "");
                String[] parts = sub.split("/");
                if (parts.length >= 1 && !parts[0].isBlank() && !isIgnoredSegment(parts[0])) {
                    return parts[0];
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractHostFromUrl(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI uri = URI.create(com.subdual.research_service.util.UrlNormalizer.unwrapLink(url));
            String host = uri.getHost();
            if (host == null) return "";
            host = host.replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT);
            if (com.subdual.research_service.util.UrlNormalizer.isLinkedInInternational(host)) {
                return "linkedin.com";
            }
            return host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isSingleTokenName(ResearchTarget target) {
        if (target == null || target.displayName() == null) return false;
        String name = target.displayName().trim();
        return !name.contains(" ") && !name.contains("-");
    }

    private boolean hasConflictingOrganization(String lowerText, String docTitle, ResearchTarget target) {
        if (target == null || target.seedOrganization() == null || target.seedOrganization().isBlank()) {
            return false;
        }

        String seedOrg = target.seedOrganization().toLowerCase(Locale.ROOT);
        String normSeedOrg = OrganizationNormalizer.normalize(target.seedOrganization()).toLowerCase(Locale.ROOT);

        boolean textContainsSeed = lowerText.contains(seedOrg);
        if (!textContainsSeed && !normSeedOrg.isBlank() && normSeedOrg.length() >= 2) {
            textContainsSeed = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(normSeedOrg) + "\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(lowerText).find();
        }
        if (textContainsSeed) {
            return false;
        }

        if (target.metadata() != null && target.metadata().containsKey("conflictingOrganizations")) {
            Object obj = target.metadata().get("conflictingOrganizations");
            if (obj instanceof java.util.Collection<?> col) {
                for (Object item : col) {
                    if (item != null) {
                        String conflictOrg = item.toString().toLowerCase(Locale.ROOT).trim();
                        if (!conflictOrg.isBlank() && lowerText.contains(conflictOrg)) {
                            return true;
                        }
                    }
                }
            }
        }

        if (docTitle != null && !docTitle.isBlank() && target.displayName() != null) {
            String lowerTitle = docTitle.toLowerCase(Locale.ROOT);
            String lowerName = target.displayName().toLowerCase(Locale.ROOT);
            if (lowerTitle.contains(lowerName)) {
                for (String sep : new String[]{" - ", " | ", " @ ", " at "}) {
                    int idx = lowerTitle.indexOf(sep);
                    if (idx > 0) {
                        String suffix = lowerTitle.substring(idx + sep.length()).trim();
                        boolean suffixContainsSeed = suffix.contains(seedOrg);
                        if (!suffixContainsSeed && !normSeedOrg.isBlank() && normSeedOrg.length() >= 2) {
                            suffixContainsSeed = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(normSeedOrg) + "\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(suffix).find();
                        }
                        if (!suffix.isBlank() && !suffixContainsSeed) {
                            if (suffix.length() >= 3 && !isGenericTitleSuffix(suffix, target)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean isGenericTitleSuffix(String suffix, ResearchTarget target) {
        String s = suffix.toLowerCase(Locale.ROOT);
        if (s.contains("linkedin") || s.contains("profile") || s.contains("overview") || s.contains("about") || s.contains("home") || s.contains("posts")) {
            return true;
        }
        if (target.seedRole() != null && s.contains(target.seedRole().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    private String getTargetContext(ResearchTarget target) {
        StringBuilder sb = new StringBuilder();
        if (target.displayName() != null) sb.append(target.displayName()).append(" ");
        if (target.seedOrganization() != null) sb.append(target.seedOrganization()).append(" ");
        if (target.seedRole() != null) sb.append(target.seedRole()).append(" ");
        if (target.metadata() != null) {
            target.metadata().values().forEach(v -> sb.append(v).append(" "));
        }
        return sb.toString();
    }

    private String extractSlug(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                String seg = segments[i].trim();
                if (!seg.isBlank() && !isIgnoredSegment(seg)) {
                    return seg;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isIgnoredSegment(String seg) {
        String lower = seg.toLowerCase(Locale.ROOT);
        return lower.equals("in") || lower.equals("profile") || lower.equals("users")
                || lower.equals("company") || lower.equals("org");
    }

    private boolean matchesSlugOrUrl(String targetSlug, String canonicalUrl, ExtractedDocument document) {
        if (targetSlug != null && !targetSlug.isBlank()) {
            String lowerSlug = targetSlug.toLowerCase(Locale.ROOT);
            if (document.url() != null && document.url().toLowerCase(Locale.ROOT).contains(lowerSlug)) {
                return true;
            }
            if (document.cleanText() != null && document.cleanText().toLowerCase(Locale.ROOT).contains(lowerSlug)) {
                return true;
            }
        }
        if (canonicalUrl != null && !canonicalUrl.isBlank()) {
            String cleanTarget = canonicalUrl.replaceFirst("^https?://(www\\.)?", "").replaceFirst("/+$", "").toLowerCase(Locale.ROOT);
            if (document.cleanText() != null && document.cleanText().toLowerCase(Locale.ROOT).contains(cleanTarget)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMetadataSignals(ResearchTarget target, ExtractedDocument document) {
        return matchesMetadataSignals(target, document, null);
    }

    private boolean matchesMetadataSignals(ResearchTarget target, ExtractedDocument document, java.util.List<String> signals) {
        if (target == null || document == null) {
            return false;
        }
        String docContent = extractFullDocumentText(document);
        boolean matched = false;

        if (target.seedOrganization() != null && !target.seedOrganization().isBlank()) {
            String seedOrg = target.seedOrganization().toLowerCase(Locale.ROOT);
            String normOrg = OrganizationNormalizer.normalize(target.seedOrganization()).toLowerCase(Locale.ROOT);
            if (docContent.contains(seedOrg) || (!normOrg.isBlank() && docContent.contains(normOrg))) {
                if (signals != null) signals.add("ORGANIZATION_MATCH: " + target.seedOrganization());
                matched = true;
            }
        }
        if (target.seedRole() != null && !target.seedRole().isBlank()) {
            String seedRole = target.seedRole().toLowerCase(Locale.ROOT);
            if (docContent.contains(seedRole)) {
                if (signals != null) signals.add("ROLE_MATCH: " + target.seedRole());
                matched = true;
            }
        }

        if (target.metadata() != null && !target.metadata().isEmpty()) {
            for (Map.Entry<String, Object> entry : target.metadata().entrySet()) {
                if (entry.getValue() != null) {
                    String key = entry.getKey().toLowerCase(Locale.ROOT);
                    if (key.equals("depth") || key.equals("targetfields") || key.equals("entityid")) {
                        continue;
                    }
                    String val = entry.getValue().toString().trim().toLowerCase(Locale.ROOT);
                    if (val.length() >= 3 && docContent.contains(val)) {
                        if (signals != null) signals.add("METADATA_" + key.toUpperCase(Locale.ROOT) + "_MATCH");
                        matched = true;
                    }
                }
            }
        }
        return matched;
    }

    private boolean isAnchoredPersonTarget(ResearchTarget target) {
        if (target == null) return false;
        if (target.entityType() == EntityType.PERSON) return true;
        if (target.canonicalUrl() != null && target.canonicalUrl().contains("linkedin.com/in/")) return true;
        return false;
    }

    private boolean hasAnchorOrMetadata(ResearchTarget target) {
        if (target == null) return false;
        if (target.canonicalUrl() != null && !target.canonicalUrl().isBlank()) return true;
        if (target.metadata() != null && !target.metadata().isEmpty()) return true;
        return false;
    }

    private boolean hasDistinctDisplayName(ResearchTarget target) {
        String name = target.displayName();
        return name != null && !name.isBlank() && !name.equalsIgnoreCase(target.canonicalUrl());
    }

    private ResolutionResult resolveWithoutDisplayName(String canonicalUrl, String docUrl) {
        if (matchesDomainAlignment(canonicalUrl, docUrl)) {
            return new ResolutionResult(ConfidenceTier.MEDIUM, MatchStatus.MATCHED, true, "Domain-aligned discovery match", 0.70, java.util.List.of("DOMAIN_ALIGNMENT_MATCH"));
        }
        return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "No host or entity name correlation found", 0.10, java.util.List.of("NO_CORRELATION"));
    }

    private boolean matchesDomainAlignment(String canonicalUrl, String docUrl) {
        try {
            URI targetUri = URI.create(canonicalUrl);
            URI docUri = URI.create(docUrl);
            String targetHost = extractHost(targetUri);
            String docHost = extractHost(docUri);

            return !targetHost.isBlank() && (
                    docHost.endsWith("." + targetHost)
                            || targetHost.endsWith("." + docHost)
                            || docHost.equalsIgnoreCase(targetHost)
            );
        } catch (Exception ignored) {
            return false;
        }
    }

    private ResolutionResult resolveByEntityName(ResearchTarget target, ExtractedDocument document) {
        String displayName = target.displayName();
        String lowerName = displayName.toLowerCase(Locale.ROOT);
        String normName = NameNormalizer.normalize(displayName).toLowerCase(Locale.ROOT);
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";
        String docText = document.cleanText() != null ? document.cleanText().toLowerCase(Locale.ROOT) : "";

        boolean titleContains = docTitle.contains(lowerName) || (!normName.isBlank() && docTitle.contains(normName));
        boolean textContains = docText.contains(lowerName) || (!normName.isBlank() && docText.contains(normName));
        boolean fuzzyTitle = false;

        if (!titleContains && !normName.isBlank() && !docTitle.isBlank()) {
            fuzzyTitle = FuzzyMatcher.isFuzzyMatch(normName, docTitle, 0.85);
        }

        if (!titleContains && !textContains && !fuzzyTitle) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "Entity name not found in document content", 0.10, java.util.List.of("NAME_NOT_FOUND"));
        }

        boolean isPerson = target.entityType() == EntityType.PERSON;
        boolean hasContext = matchesMetadataSignals(target, document);

        if (isSingleTokenName(target) && !hasContext) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Ambiguous: First-name only match without corroborating identity signals", 0.30, java.util.List.of("AMBIGUOUS_FIRST_NAME_ONLY"));
        }

        if (isPerson && !hasContext && hasAnchorOrMetadata(target)) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Ambiguous: Name-only match without corroborating identity signals", 0.35, java.util.List.of("AMBIGUOUS_NAME_ONLY"));
        }

        if (titleContains || fuzzyTitle) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name matched in document title", 0.90, java.util.List.of(titleContains ? "TITLE_NAME_MATCH" : "TITLE_FUZZY_NAME_MATCH"));
        }
        return new ResolutionResult(ConfidenceTier.MEDIUM, MatchStatus.MATCHED, true, "Entity name mentioned in document body", 0.70, java.util.List.of("BODY_NAME_MATCH"));
    }

    public ResolutionResult resolveByEntityName(String displayName, ExtractedDocument document) {
        if (displayName == null || displayName.isBlank()) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "Empty display name", 0.0, java.util.List.of("EMPTY_NAME"));
        }
        String lowerName = displayName.toLowerCase(Locale.ROOT);
        String normName = NameNormalizer.normalize(displayName).toLowerCase(Locale.ROOT);
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";
        String docText = document.cleanText() != null ? document.cleanText().toLowerCase(Locale.ROOT) : "";

        if (docTitle.contains(lowerName) || (!normName.isBlank() && docTitle.contains(normName))) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name matched in document title", 0.90, java.util.List.of("TITLE_NAME_MATCH"));
        }
        if (docText.contains(lowerName) || (!normName.isBlank() && docText.contains(normName))) {
            return new ResolutionResult(ConfidenceTier.MEDIUM, MatchStatus.MATCHED, true, "Entity name mentioned in document body", 0.70, java.util.List.of("BODY_NAME_MATCH"));
        }
        if (!normName.isBlank() && !docTitle.isBlank() && FuzzyMatcher.isFuzzyMatch(normName, docTitle, 0.85)) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name matched in document title", 0.85, java.util.List.of("TITLE_FUZZY_NAME_MATCH"));
        }
        return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "Entity name not found in document content", 0.10, java.util.List.of("NAME_NOT_FOUND"));
    }

    private String extractHost(URI uri) {
        return uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
    }
}
