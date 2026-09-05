package com.subdual.research_service.extraction;

import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.ResearchTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
@Slf4j
public class EntityResolver {

    public record ResolutionResult(ConfidenceTier confidence, boolean matched, String reason) {}

    public ResolutionResult resolve(ResearchTarget target, ExtractedDocument document) {
        if (target == null || document == null || document.url() == null) {
            return new ResolutionResult(ConfidenceTier.UNKNOWN, false, "Missing target or document");
        }

        if (matchesCanonicalHost(target.canonicalUrl(), document.url())) {
            return new ResolutionResult(ConfidenceTier.HIGH, true, "Host match with target canonical URL");
        }

        if (!hasDistinctDisplayName(target)) {
            return resolveWithoutDisplayName(target.canonicalUrl(), document.url());
        }

        return resolveByEntityName(target.displayName(), document);
    }

    private boolean matchesCanonicalHost(String canonicalUrl, String docUrl) {
        try {
            URI targetUri = URI.create(canonicalUrl);
            URI docUri = URI.create(docUrl);
            return targetUri.getHost() != null
                    && docUri.getHost() != null
                    && targetUri.getHost().equalsIgnoreCase(docUri.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasDistinctDisplayName(ResearchTarget target) {
        String name = target.displayName();
        return name != null && !name.isBlank() && !name.equalsIgnoreCase(target.canonicalUrl());
    }

    private ResolutionResult resolveWithoutDisplayName(String canonicalUrl, String docUrl) {
        if (matchesDomainAlignment(canonicalUrl, docUrl)) {
            return new ResolutionResult(ConfidenceTier.MEDIUM, true, "Domain-aligned discovery match");
        }
        return new ResolutionResult(ConfidenceTier.LOW, false, "No host or entity name correlation found");
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

    private ResolutionResult resolveByEntityName(String displayName, ExtractedDocument document) {
        String lowerName = displayName.toLowerCase(Locale.ROOT);
        String docTitle = document.title() != null ? document.title().toLowerCase(Locale.ROOT) : "";
        String docText = document.cleanText() != null ? document.cleanText().toLowerCase(Locale.ROOT) : "";

        if (docTitle.contains(lowerName)) {
            return new ResolutionResult(ConfidenceTier.HIGH, true, "Entity name matched in document title");
        }
        if (docText.contains(lowerName)) {
            return new ResolutionResult(ConfidenceTier.MEDIUM, true, "Entity name mentioned in document body");
        }
        return new ResolutionResult(ConfidenceTier.LOW, false, "Entity name not found in document content");
    }

    private String extractHost(URI uri) {
        return uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
    }
}
