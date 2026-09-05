package com.subdual.research_service.discovery;

import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

@Component
public class QueryBuilder {

    public List<String> buildDiscoveryQueries(ResearchTarget target) {
        if (target == null) {
            return List.of();
        }

        List<String> queries = new ArrayList<>();
        String primary = buildDiscoveryQuery(target);
        if (!primary.isBlank()) {
            queries.add(primary);
        }

        if (hasDistinctDisplayName(target)) {
            String name = target.displayName().trim();
            if (target.seedOrganization() != null && !target.seedOrganization().isBlank()) {
                queries.add("\"" + name + "\" \"" + target.seedOrganization().trim() + "\"");
            }
            if (target.seedRole() != null && !target.seedRole().isBlank()) {
                queries.add("\"" + name + "\" \"" + target.seedRole().trim() + "\"");
            }
        }

        return queries.stream().distinct().limit(3).toList();
    }

    public String buildAdaptiveQuery(ResearchTarget target, List<String> missingFields) {
        if (target == null || missingFields == null || missingFields.isEmpty()) {
            return buildDiscoveryQuery(target);
        }

        String name = hasDistinctDisplayName(target) ? target.displayName().trim() : "";
        String fieldTerm = normalizeFieldForQuery(missingFields.get(0));

        if (!name.isBlank()) {
            if (target.seedOrganization() != null && !target.seedOrganization().isBlank()) {
                return "\"" + name + "\" " + fieldTerm + " \"" + target.seedOrganization().trim() + "\"";
            }
            return "\"" + name + "\" " + fieldTerm;
        }

        return fieldTerm + " " + buildDiscoveryQuery(target);
    }

    private String normalizeFieldForQuery(String field) {
        if (field == null) return "overview";
        String lower = field.toLowerCase(Locale.ROOT).trim();
        return switch (lower) {
            case "currentrole", "role" -> "role position";
            case "organization", "current_organization", "company" -> "organization company";
            case "education", "degree", "alumni" -> "education university college";
            case "location", "headquarters", "based_in" -> "location headquarters";
            case "skills", "technologies" -> "skills technologies";
            case "industry" -> "industry sector";
            case "products", "services" -> "products services";
            default -> lower;
        };
    }

    public String buildDiscoveryQuery(ResearchTarget target) {
        if (target == null) {
            return "";
        }

        boolean hasUrl = hasValidHttpUrl(target);
        boolean hasName = hasDistinctDisplayName(target);

        if (hasUrl && hasName) {
            return buildUrlAndNameQuery(target);
        } else if (hasUrl) {
            return buildUrlOnlyQuery(target);
        } else if (hasName) {
            return buildNameOnlyQuery(target);
        }

        return target.canonicalUrl() != null ? target.canonicalUrl() : "";
    }

    private boolean hasValidHttpUrl(ResearchTarget target) {
        String url = target.canonicalUrl();
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private boolean hasDistinctDisplayName(ResearchTarget target) {
        String name = target.displayName();
        return name != null
                && !name.isBlank()
                && !name.equalsIgnoreCase(target.canonicalUrl())
                && !name.equalsIgnoreCase(target.rawUrl());
    }

    private String buildUrlAndNameQuery(ResearchTarget target) {
        String name = target.displayName().trim();
        try {
            URI uri = URI.create(target.canonicalUrl());
            String host = extractCleanHost(uri);
            String pathIdentifier = extractProfileOrPathIdentifier(uri);

            if (!pathIdentifier.isBlank()) {
                return "\"" + name + "\" " + host + "/" + pathIdentifier;
            }
            if (!host.isBlank()) {
                return "\"" + name + "\" " + host + " " + resolveTypeKeyword(target.entityType());
            }
        } catch (Exception ignored) {}

        return name + " " + resolveTypeKeyword(target.entityType());
    }

    private String buildUrlOnlyQuery(ResearchTarget target) {
        try {
            URI uri = URI.create(target.canonicalUrl());
            String host = extractCleanHost(uri);
            String path = uri.getPath() != null ? uri.getPath().replace("/", " ").trim() : "";
            String typeKeyword = resolveTypeKeyword(target.entityType());

            if (host.equalsIgnoreCase("github.com") && !path.isBlank()) {
                return (path + " repository").trim();
            }
            return (host + " " + path + " " + typeKeyword).trim();
        } catch (Exception e) {
            return target.canonicalUrl();
        }
    }

    private String buildNameOnlyQuery(ResearchTarget target) {
        String name = target.displayName().trim();
        return switch (target.entityType() != null ? target.entityType() : EntityType.OTHER) {
            case ORGANIZATION -> name + " company official";
            case REPOSITORY -> name + " repository source code";
            case PERSON -> name + " profile biography";
            case PRODUCT -> name + " product overview";
            case WEBSITE -> name + " official website";
            case OTHER -> name + " overview";
        };
    }

    private String extractCleanHost(URI uri) {
        return uri.getHost() != null ? uri.getHost().replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : "";
    }

    private String extractProfileOrPathIdentifier(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.equals("/")) {
            return "";
        }
        return path.replaceAll("^/|/$", "").trim();
    }

    String resolveTypeKeyword(EntityType entityType) {
        if (entityType == null) return "overview";
        return switch (entityType) {
            case ORGANIZATION -> "company";
            case REPOSITORY -> "repository";
            case PERSON -> "profile";
            case PRODUCT -> "product";
            case WEBSITE -> "official";
            case OTHER -> "overview";
        };
    }
}
