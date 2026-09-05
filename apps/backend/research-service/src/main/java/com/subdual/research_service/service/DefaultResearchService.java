package com.subdual.research_service.service;

import com.subdual.research_service.client.ResearchSourceClient;
import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchStatus;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import com.subdual.research_service.dto.ResearchResult;
import com.subdual.research_service.dto.SourceItem;
import com.subdual.research_service.exception.BusinessRuleException;
import com.subdual.research_service.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultResearchService implements ResearchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchService.class);

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "ref_src", "fbclid", "gclid", "source", "feature"
    );

    private final ResearchSourceClient researchSourceClient;
    private final ResearchDiscoveryProperties discoveryProperties;

    @Override
    public ResearchResponse executeResearch(ResearchRequest request) {
        return research(request);
    }

    @Override
    public ResearchResponse research(ResearchRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Research request received for URL: '{}', entityType: '{}', name: '{}'",
                request != null ? request.url() : null,
                request != null ? request.entityType() : null,
                request != null ? request.name() : null);

        validateRequest(request);
        ResearchTarget target = buildTarget(request);
        List<ResearchSource> sources = discoverSources(target);
        return buildResponse(target, sources, startTime);
    }

    private void validateRequest(ResearchRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Request body must not be null");
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new BusinessRuleException("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
        }
    }

    private ResearchTarget buildTarget(ResearchRequest request) {
        String canonicalUrl = canonicalizeUrl(request.url());
        String entityId = computeEntityId(canonicalUrl);
        EntityType type = request.entityType() != null ? request.entityType() : EntityType.OTHER;
        String displayName = (request.name() != null && !request.name().isBlank())
                ? request.name().trim()
                : canonicalUrl;

        return new ResearchTarget(request.url(), canonicalUrl, entityId, type, displayName);
    }

    private List<ResearchSource> discoverSources(ResearchTarget target) {
        String query = buildDiscoveryQuery(target);
        log.info("Source discovery started for entityId: '{}', query: '{}'", target.entityId(), query);

        List<DiscoveredSource> discovered;
        try {
            discovered = researchSourceClient.discoverSources(query, discoveryProperties.maxResults());
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (ExternalServiceException ex) {
            log.error("Provider failure during source discovery for query '{}': {}", query, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("Provider failure during source discovery for query '{}'", query, ex);
            throw new ExternalServiceException("Failed to retrieve research sources", ex);
        }

        List<ResearchSource> validSources = processDiscoveredSources(discovered, target);
        log.info("Source discovery completed for entityId: '{}', found {} valid sources",
                target.entityId(), validSources.size());
        return validSources;
    }

    private List<ResearchSource> processDiscoveredSources(List<DiscoveredSource> rawSources, ResearchTarget target) {
        if (rawSources == null || rawSources.isEmpty()) {
            return List.of();
        }

        Set<String> seenUrls = new HashSet<>();
        List<ResearchSource> processed = new ArrayList<>();

        for (DiscoveredSource ds : rawSources) {
            if (ds == null || ds.url() == null || ds.url().isBlank()) {
                continue;
            }
            String normalizedUrl = normalizeDiscoveredUrl(ds.url());
            if (normalizedUrl == null || seenUrls.contains(normalizedUrl)) {
                continue;
            }
            seenUrls.add(normalizedUrl);

            String title = (ds.title() != null && !ds.title().isBlank())
                    ? ds.title().trim()
                    : normalizedUrl;
            String sourceType = (ds.sourceType() != null && !ds.sourceType().isBlank())
                    ? ds.sourceType().trim()
                    : classifySourceType(normalizedUrl, target);
            Instant retrievedAt = ds.retrievedAt() != null ? ds.retrievedAt() : Instant.now();
            Double relevance = ds.relevance() != null ? ds.relevance() : 0.80;

            processed.add(new ResearchSource(normalizedUrl, title, sourceType, retrievedAt, relevance));
        }

        processed.sort(Comparator.comparingDouble(ResearchSource::relevance).reversed());
        return processed;
    }

    private ResearchResponse buildResponse(ResearchTarget target, List<ResearchSource> sources, long startTime) {
        List<SourceItem> sourceItems = sources.stream()
                .map(this::mapSource)
                .toList();

        ResearchResult result = new ResearchResult(
                target.displayName(),
                target.entityType(),
                target.canonicalUrl(),
                Map.of()
        );

        long executionTimeMs = System.currentTimeMillis() - startTime;
        ResearchStatus status = ResearchStatus.COMPLETED;

        log.info("Research completed for entityId: '{}' in {}ms with status: {}",
                target.entityId(), executionTimeMs, status);

        return new ResearchResponse(
                status,
                target.entityId(),
                result,
                sourceItems,
                executionTimeMs
        );
    }

    private SourceItem mapSource(ResearchSource source) {
        return new SourceItem(
                source.url(),
                source.title(),
                source.sourceType(),
                source.retrievedAt(),
                source.relevance()
        );
    }

    private String buildDiscoveryQuery(ResearchTarget target) {
        if (target.displayName() != null && !target.displayName().equals(target.canonicalUrl())) {
            return target.displayName() + " " + target.entityType().name().toLowerCase(Locale.ROOT);
        }

        try {
            URI uri = URI.create(target.canonicalUrl());
            String host = uri.getHost() != null ? uri.getHost() : "";
            String path = uri.getPath() != null ? uri.getPath().replace("/", " ").trim() : "";
            return (host + " " + path + " " + target.entityType().name().toLowerCase(Locale.ROOT)).trim();
        } catch (Exception e) {
            return target.canonicalUrl();
        }
    }

    private String normalizeDiscoveredUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return null;
            }
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
            if (host == null || host.isBlank()) {
                return null;
            }

            int port = uri.getPort();
            String portPart = (port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
                    ? "" : ":" + port;

            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }

            // Strip tracking query parameters
            String cleanQuery = "";
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                String filtered = Arrays.stream(uri.getQuery().split("&"))
                        .filter(param -> {
                            String paramName = param.split("=")[0].toLowerCase(Locale.ROOT);
                            return !TRACKING_PARAMS.contains(paramName);
                        })
                        .collect(Collectors.joining("&"));
                if (!filtered.isBlank()) {
                    cleanQuery = "?" + filtered;
                }
            }

            return scheme + "://" + host + portPart + path + cleanQuery;
        } catch (Exception ex) {
            return null;
        }
    }

    private String classifySourceType(String url, ResearchTarget target) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("github.com")) {
            return "GITHUB";
        }
        if (lowerUrl.contains("docs.") || lowerUrl.contains("/docs") || lowerUrl.contains("/reference")) {
            return "DOCUMENTATION";
        }
        if (lowerUrl.contains("linkedin.com") || lowerUrl.contains("twitter.com") || lowerUrl.contains("x.com")) {
            return "SOCIAL_PROFILE";
        }
        if (lowerUrl.contains("blog.") || lowerUrl.contains("/blog")) {
            return "BLOG";
        }
        try {
            URI targetUri = URI.create(target.canonicalUrl());
            URI sourceUri = URI.create(url);
            if (targetUri.getHost() != null && targetUri.getHost().equalsIgnoreCase(sourceUri.getHost())) {
                return "OFFICIAL_WEBSITE";
            }
        } catch (Exception ignored) {}

        return "SEARCH_RESULT";
    }

    private String canonicalizeUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            int port = uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";

            String portPart = (port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
                    ? "" : ":" + port;

            return scheme + "://" + host + portPart + path + query;
        } catch (Exception e) {
            return rawUrl.trim();
        }
    }

    private String computeEntityId(String canonicalUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
