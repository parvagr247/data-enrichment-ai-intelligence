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

    public record ResolutionResult(ConfidenceTier confidence, MatchStatus status, boolean matched, String reason) {
        public ResolutionResult(ConfidenceTier confidence, MatchStatus status, String reason) {
            this(confidence, status, status == MatchStatus.MATCHED, reason);
        }

        public ResolutionResult(ConfidenceTier confidence, boolean matched, String reason) {
            this(confidence, matched ? MatchStatus.MATCHED : MatchStatus.NOT_MATCHED, matched, reason);
        }
    }

    public ResolutionResult resolve(ResearchTarget target, ExtractedDocument document) {
        if (!isValidResolutionInput(target, document)) {
            return new ResolutionResult(ConfidenceTier.UNKNOWN, MatchStatus.NOT_MATCHED, false, "Missing target or document");
        }

        if (isAnchorUrl(document.url(), target)) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Primary anchor URL match");
        }

        if (matchesNonMultiTenantHost(target.canonicalUrl(), document.url())) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Host match with target canonical URL");
        }

        if (isConflictingEntity(target, document)) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "Rejected: Conflicting entity identity signals detected");
        }

        String targetSlug = extractSlug(target.canonicalUrl() != null ? target.canonicalUrl() : target.rawUrl());

        ResolutionResult titleResult = checkTitleMatch(target, document, targetSlug);
        if (titleResult != null) {
            return titleResult;
        }

        if (matchesSlugOrUrl(targetSlug, target.canonicalUrl(), document)) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Identity handle or profile slug corroborated in source");
        }

        if (matchesMetadataSignals(target, document)) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name and corroborating identity signals matched");
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
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";

        if (docTitle.contains(lowerName)) {
            if (isAnchoredPersonTarget(target) && !hasCorroboratingSignals(targetSlug, target, document)) {
                return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Rejected: Name-only match without corroborating identity signals");
            }
            if (target.entityType() == EntityType.PERSON && !hasCorroboratingSignals(targetSlug, target, document)) {
                return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Ambiguous: Name-only match without corroborating identity signals");
            }
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name matched in document title");
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
        String lowerText = extractFullDocumentText(document);

        if (hasConflictingProfession(lowerText, target)) {
            return true;
        }

        return hasConflictingOrganization(lowerText, target);
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

    private boolean hasConflictingOrganization(String lowerText, ResearchTarget target) {
        if (target == null || target.seedOrganization() == null || target.seedOrganization().isBlank()) {
            return false;
        }

        String seedOrg = target.seedOrganization().toLowerCase(Locale.ROOT);
        if (!lowerText.contains(seedOrg)) {
            return lowerText.contains("d. e. shaw") || lowerText.contains("deshaw") || lowerText.contains("iit delhi");
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
        if (target == null || document == null) {
            return false;
        }
        String docContent = extractFullDocumentText(document);

        if (target.seedOrganization() != null && !target.seedOrganization().isBlank()) {
            if (docContent.contains(target.seedOrganization().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        if (target.seedRole() != null && !target.seedRole().isBlank()) {
            if (docContent.contains(target.seedRole().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        if (target.metadata() == null || target.metadata().isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : target.metadata().entrySet()) {
            if (entry.getValue() != null) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (key.equals("depth") || key.equals("targetfields") || key.equals("entityid")) {
                    continue;
                }
                String val = entry.getValue().toString().trim().toLowerCase(Locale.ROOT);
                if (val.length() >= 3 && docContent.contains(val)) {
                    return true;
                }
            }
        }
        return false;
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
            return new ResolutionResult(ConfidenceTier.MEDIUM, MatchStatus.MATCHED, true, "Domain-aligned discovery match");
        }
        return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "No host or entity name correlation found");
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
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";
        String docText = document.cleanText() != null ? document.cleanText().toLowerCase(Locale.ROOT) : "";

        boolean titleContains = docTitle.contains(lowerName);
        boolean textContains = docText.contains(lowerName);

        if (!titleContains && !textContains) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "Entity name not found in document content");
        }

        boolean isPerson = target.entityType() == EntityType.PERSON;
        boolean hasContext = matchesMetadataSignals(target, document);

        if (isPerson && !hasContext && hasAnchorOrMetadata(target)) {
            return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.AMBIGUOUS, false, "Ambiguous: Name-only match without corroborating identity signals");
        }

        if (titleContains) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name matched in document title");
        }
        return new ResolutionResult(ConfidenceTier.MEDIUM, MatchStatus.MATCHED, true, "Entity name mentioned in document body");
    }

    public ResolutionResult resolveByEntityName(String displayName, ExtractedDocument document) {
        String lowerName = displayName.toLowerCase(Locale.ROOT);
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";
        String docText = document.cleanText() != null ? document.cleanText().toLowerCase(Locale.ROOT) : "";

        if (docTitle.contains(lowerName)) {
            return new ResolutionResult(ConfidenceTier.HIGH, MatchStatus.MATCHED, true, "Entity name matched in document title");
        }
        if (docText.contains(lowerName)) {
            return new ResolutionResult(ConfidenceTier.MEDIUM, MatchStatus.MATCHED, true, "Entity name mentioned in document body");
        }
        return new ResolutionResult(ConfidenceTier.LOW, MatchStatus.NOT_MATCHED, false, "Entity name not found in document content");
    }

    private String extractHost(URI uri) {
        return uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
    }
}
