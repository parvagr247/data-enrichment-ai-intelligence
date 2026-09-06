package com.subdual.research_service.discovery;

import com.subdual.research_service.discovery.model.QueryIntent;
import com.subdual.research_service.discovery.model.QueryStrategy;
import com.subdual.research_service.discovery.model.ResearchQuery;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class QueryBuilder {

    public List<String> buildDiscoveryQueries(ResearchTarget target) {
        if (!isValidTarget(target)) {
            return List.of();
        }

        List<ResearchQuery> queries = buildRequirementQueries(target);
        if (queries.isEmpty()) {
            List<String> fallback = new ArrayList<>();
            addPrimaryQuery(fallback, target);
            addAlternativeQueries(fallback, target);
            return fallback.stream().distinct().limit(5).toList();
        }

        return queries.stream().map(ResearchQuery::queryText).distinct().limit(5).toList();
    }

    public List<ResearchQuery> buildRequirementQueries(ResearchTarget target) {
        if (!isValidTarget(target)) {
            return List.of();
        }

        List<ResearchQuery> queries = new ArrayList<>();
        String name = hasDistinctDisplayName(target) ? target.displayName().trim() : "";

        // Primary Query: Identity-anchored discovery query
        String primary = buildDiscoveryQuery(target);
        if (!primary.isBlank()) {
            queries.add(new ResearchQuery(
                    primary,
                    QueryIntent.IDENTITY,
                    QueryStrategy.CANONICAL_DOMAIN
            ));
        }

        // Strategy A: EXACT_NAME_AND_ORG
        String org = findContextValue(target, "organization", "company", "employer", "current_organization");
        if (!name.isBlank() && org != null && !org.isBlank()) {
            queries.add(new ResearchQuery(
                    "\"" + name + "\" \"" + org.trim() + "\"",
                    QueryIntent.ORGANIZATION,
                    QueryStrategy.EXACT_NAME_AND_ORG
            ));
        }

        // Strategy B: NAME_AND_FIELD
        if (!name.isBlank() && target.targetFields() != null) {
            for (String field : target.targetFields()) {
                QueryIntent intent = mapFieldToIntent(field);
                String fieldTerm = normalizeFieldForQuery(field);
                queries.add(new ResearchQuery(
                        "\"" + name + "\" " + fieldTerm,
                        intent,
                        QueryStrategy.NAME_AND_FIELD
                ));
            }
        }

        // Strategy C: CANONICAL_DOMAIN
        if (hasValidHttpUrl(target)) {
            queries.add(new ResearchQuery(
                    buildUrlOnlyQuery(target),
                    QueryIntent.IDENTITY,
                    QueryStrategy.CANONICAL_DOMAIN
            ));
        }

        return queries.stream()
                .filter(q -> q.queryText() != null && !q.queryText().isBlank())
                .distinct()
                .limit(6)
                .toList();
    }

    public List<ResearchQuery> buildAdaptiveResearchQueries(ResearchTarget target, List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty() || !isValidTarget(target)) {
            return buildRequirementQueries(target);
        }

        List<ResearchQuery> queries = new ArrayList<>();
        for (String field : missingFields) {
            QueryIntent intent = mapFieldToIntent(field);
            String queryText = constructAdaptiveQuery(target, normalizeFieldForQuery(field));
            queries.add(new ResearchQuery(queryText, intent, QueryStrategy.NAME_AND_FIELD));
        }
        return queries;
    }

    public static QueryIntent mapFieldToIntent(String field) {
        if (field == null) return QueryIntent.GENERAL_PROFILE;
        String lower = field.toLowerCase(Locale.ROOT).trim();
        return switch (lower) {
            case "role", "title", "position", "currentrole" -> QueryIntent.ROLE;
            case "organization", "company", "employer" -> QueryIntent.ORGANIZATION;
            case "education", "degree", "university", "college", "alumni" -> QueryIntent.EDUCATION;
            case "location", "city", "country", "headquarters", "based_in" -> QueryIntent.LOCATION;
            case "skills", "technologies", "tech_stack", "languages" -> QueryIntent.TECHNOLOGY;
            case "product", "products", "services" -> QueryIntent.PRODUCT;
            default -> QueryIntent.GENERAL_PROFILE;
        };
    }

    public String buildAdaptiveQuery(ResearchTarget target, List<String> missingFields) {
        if (!hasMissingFields(missingFields) || !isValidTarget(target)) {
            return buildDiscoveryQuery(target);
        }

        String fieldTerm = normalizeFieldForQuery(missingFields.get(0));
        return constructAdaptiveQuery(target, fieldTerm);
    }

    public String buildDiscoveryQuery(ResearchTarget target) {
        if (!isValidTarget(target)) {
            return "";
        }

        if (hasUrlAndName(target)) {
            return buildUrlAndNameQuery(target);
        }

        if (hasValidHttpUrl(target)) {
            return buildUrlOnlyQuery(target);
        }

        if (hasDistinctDisplayName(target)) {
            return buildNameOnlyQuery(target);
        }

        return resolveFallbackQuery(target);
    }

    private boolean isValidTarget(ResearchTarget target) {
        return target != null;
    }

    private boolean hasMissingFields(List<String> missingFields) {
        return missingFields != null && !missingFields.isEmpty();
    }

    private boolean hasUrlAndName(ResearchTarget target) {
        return hasValidHttpUrl(target) && hasDistinctDisplayName(target);
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

    private void addPrimaryQuery(List<String> queries, ResearchTarget target) {
        String primary = buildDiscoveryQuery(target);
        if (!primary.isBlank()) {
            queries.add(primary);
        }
    }

    private void addAlternativeQueries(List<String> queries, ResearchTarget target) {
        if (!hasDistinctDisplayName(target)) {
            return;
        }

        String name = target.displayName().trim();
        String org = findContextValue(target, "organization", "company", "employer", "current_organization");
        if (org != null && !org.isBlank()) {
            queries.add("\"" + name + "\" \"" + org.trim() + "\"");
        }
        String role = findContextValue(target, "role", "title", "position", "currentrole", "headline");
        if (role != null && !role.isBlank()) {
            queries.add("\"" + name + "\" \"" + role.trim() + "\"");
        }
        String location = findContextValue(target, "location", "city", "country", "headquarters", "based_in");
        if (location != null && !location.isBlank()) {
            queries.add("\"" + name + "\" \"" + location.trim() + "\"");
        }
        if (target.targetFields() != null && !target.targetFields().isEmpty()) {
            String fieldsToken = target.targetFields().stream()
                    .limit(4)
                    .map(this::normalizeFieldForQuery)
                    .collect(java.util.stream.Collectors.joining(" "));
            queries.add("\"" + name + "\" " + fieldsToken);
        }
    }

    private String findContextValue(ResearchTarget target, String... keys) {
        if (target == null) return null;
        if (target.metadata() != null && !target.metadata().isEmpty()) {
            for (String key : keys) {
                for (var entry : target.metadata().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                        String val = entry.getValue().toString().trim();
                        if (!val.isBlank()) return val;
                    }
                }
            }
        }
        if (keys.length > 0 && "organization".equalsIgnoreCase(keys[0])) return target.seedOrganization();
        if (keys.length > 0 && "role".equalsIgnoreCase(keys[0])) return target.seedRole();
        return null;
    }

    private String constructAdaptiveQuery(ResearchTarget target, String fieldTerm) {
        String name = hasDistinctDisplayName(target) ? target.displayName().trim() : "";
        if (!name.isBlank()) {
            return buildNamedAdaptiveQuery(target, name, fieldTerm);
        }
        return fieldTerm + " " + buildDiscoveryQuery(target);
    }

    private String buildNamedAdaptiveQuery(ResearchTarget target, String name, String fieldTerm) {
        if (target.seedOrganization() != null && !target.seedOrganization().isBlank()) {
            return "\"" + name + "\" " + fieldTerm + " \"" + target.seedOrganization().trim() + "\"";
        }
        return "\"" + name + "\" " + fieldTerm;
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

    private String resolveFallbackQuery(ResearchTarget target) {
        return target.canonicalUrl() != null ? target.canonicalUrl() : "";
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
