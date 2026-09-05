package com.subdual.research_service.service;

import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchStatus;
import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import com.subdual.research_service.dto.ResearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class DefaultResearchService implements ResearchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchService.class);

    @Override
    public ResearchResponse executeResearch(ResearchRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Executing research request for URL: '{}', entityType: '{}', name: '{}'",
                request.url(), request.entityType(), request.name());

        String canonicalUrl = canonicalizeUrl(request.url());
        String entityId = computeEntityId(canonicalUrl);

        EntityType type = request.entityType() != null ? request.entityType() : EntityType.OTHER;
        String displayName = (request.name() != null && !request.name().isBlank())
                ? request.name().trim()
                : canonicalUrl;

        // Milestone 1 baseline: truthful, unmocked attributes and sources
        ResearchResult result = new ResearchResult(
                displayName,
                type,
                canonicalUrl,
                Map.of()
        );

        long executionTimeMs = System.currentTimeMillis() - startTime;

        log.info("Completed research execution context for entityId: '{}' in {}ms", entityId, executionTimeMs);

        return new ResearchResponse(
                ResearchStatus.COMPLETED,
                entityId,
                result,
                List.of(),
                executionTimeMs
        );
    }

    private String canonicalizeUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
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
