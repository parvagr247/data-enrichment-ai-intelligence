package com.subdual.research_service.discovery;

import com.subdual.research_service.domain.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class QueryBuilder {

    public String buildDiscoveryQuery(ResearchTarget target) {
        if (target == null) {
            return "";
        }

        String entityTypeName = target.entityType() != null
                ? target.entityType().name().toLowerCase(Locale.ROOT)
                : "overview";

        boolean hasDistinctDisplayName = target.displayName() != null
                && !target.displayName().isBlank()
                && !target.displayName().equalsIgnoreCase(target.canonicalUrl())
                && !target.displayName().equalsIgnoreCase(target.rawUrl());

        if (hasDistinctDisplayName) {
            if (target.entityType() == null) {
                return target.displayName().trim() + " " + entityTypeName;
            }
            return switch (target.entityType()) {
                case ORGANIZATION -> target.displayName().trim() + " company official";
                case REPOSITORY -> target.displayName().trim() + " repository source code";
                case PERSON -> target.displayName().trim() + " profile biography";
                case PRODUCT -> target.displayName().trim() + " product overview";
                case WEBSITE -> target.displayName().trim() + " official website";
                case OTHER -> target.displayName().trim() + " " + entityTypeName;
            };
        }

        try {
            URI uri = URI.create(target.canonicalUrl());
            String host = uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "") : "";
            String path = uri.getPath() != null ? uri.getPath().replace("/", " ").trim() : "";

            if (host.equalsIgnoreCase("github.com") && !path.isBlank()) {
                return (path + " repository").trim();
            }

            return (host + " " + path + " " + entityTypeName).trim();
        } catch (Exception e) {
            return target.canonicalUrl();
        }
    }
}
