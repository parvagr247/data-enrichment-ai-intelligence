package com.subdual.research_service.source;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.WebFetchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Robust HTTP client implementation of WebContentFetcher with SSRF guards,
 * timeout controls, size bounding, and deterministic mock fallback.
 */
@Component
public class DefaultWebContentFetcher implements WebContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebContentFetcher.class);

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "mysql", "enrichment-mysql", "ai-intelligent-service",
            "dataset-service", "host.docker.internal"
    );

    private final WebFetchProperties properties;
    private final HttpClient httpClient;
    private final boolean mockMode;

    public DefaultWebContentFetcher(WebFetchProperties properties) {
        this(properties, false);
    }

    @Autowired
    public DefaultWebContentFetcher(WebFetchProperties properties, ResearchDiscoveryProperties discoveryProperties) {
        this(properties, discoveryProperties != null && "mock".equalsIgnoreCase(discoveryProperties.provider()));
    }

    public DefaultWebContentFetcher(WebFetchProperties properties, boolean mockMode) {
        this.properties = properties;
        this.mockMode = mockMode;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public FetchedContent fetch(String url) {
        if (url == null || url.isBlank()) {
            return FetchedContent.failed(url, 400, "URL cannot be empty");
        }

        // Fast-path deterministic mock responses without attempting socket connections
        if (mockMode || isMockUrl(url)) {
            return generateMockContent(url);
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return FetchedContent.failed(url, 400, "Unsupported URI scheme: " + scheme);
            }

            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            if (host.isBlank() || isBlockedHost(host)) {
                log.warn("Blocked fetch request to restricted host: '{}'", host);
                return FetchedContent.failed(url, 403, "Access to host is restricted: " + host);
            }
        } catch (Exception ex) {
            log.warn("Malformed URL during content fetch: '{}'", url);
            return FetchedContent.failed(url, 400, "Malformed URL: " + ex.getMessage());
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                    .header("User-Agent", properties.userAgent())
                    .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,application/json;q=0.8")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();

            if (statusCode < 200 || statusCode >= 300) {
                log.debug("HTTP {} returned when fetching URL: '{}'", statusCode, url);
                return FetchedContent.failed(url, statusCode, "HTTP " + statusCode);
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("text/html").toLowerCase(Locale.ROOT);
            if (!isAcceptableContentType(contentType)) {
                log.debug("Skipping unsupported content type '{}' for URL: '{}'", contentType, url);
                return FetchedContent.failed(url, statusCode, "Unsupported content-type: " + contentType);
            }

            long maxBytes = (long) properties.maxResponseSizeMb() * 1024 * 1024;
            String body = readBoundedBody(response.body(), maxBytes);

            return FetchedContent.success(url, statusCode, contentType, body);
        } catch (Exception ex) {
            log.warn("Error fetching URL '{}': {}", url, ex.getMessage());
            return FetchedContent.failed(url, 500, "Fetch failed: " + ex.getMessage());
        }
    }

    private boolean isMockUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("mock-provider.local")
                || lower.contains("mock.research.local")
                || lower.contains(".local")
                || lower.contains("mock-");
    }

    private boolean isBlockedHost(String host) {
        if (BLOCKED_HOSTS.contains(host)) {
            return true;
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    return true;
                }
                byte[] raw = addr.getAddress();
                if (raw != null && raw.length == 4) {
                    int b0 = raw[0] & 0xFF;
                    int b1 = raw[1] & 0xFF;
                    if (b0 == 127 || b0 == 10 || b0 == 0) return true;
                    if (b0 == 192 && b1 == 168) return true;
                    if (b0 == 169 && b1 == 254) return true;
                    if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;
                }
            }
        } catch (Exception ignored) {
            // Unresolvable host will naturally fail on socket connection
        }

        return false;
    }

    private boolean isAcceptableContentType(String contentType) {
        return contentType.contains("text/html")
                || contentType.contains("application/xhtml+xml")
                || contentType.contains("text/plain")
                || contentType.contains("application/json");
    }

    private String readBoundedBody(InputStream in, long maxBytes) throws Exception {
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (totalRead >= maxBytes) {
                    log.debug("Response exceeded max byte limit ({}), truncating stream", maxBytes);
                    break;
                }
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private FetchedContent generateMockContent(String url) {
        String lowerUrl = url != null ? url.toLowerCase(Locale.ROOT) : "";
        String title = "[MOCK] Entity Overview and Reference";
        String description = "This is deterministic mock description for automated pipeline testing.";
        String siteName = "Mock System";
        String body = "This is deterministic mock text used during testing and offline verification.";

        if (lowerUrl.contains("spring-boot") || lowerUrl.contains("spring.io") || lowerUrl.contains("spring-projects")) {
            title = "[MOCK] Spring Boot - Production-Grade Spring Applications";
            description = "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications that you can just run.";
            siteName = "Spring";
            body = "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications that you can just run. It takes an opinionated view of the Spring platform.";
        } else if (lowerUrl.contains("jane-doe")) {
            title = "[MOCK] Jane Doe - Principal Infrastructure Engineer";
            description = "Jane Doe is a Principal Infrastructure Engineer specializing in resilient cloud platforms.";
            siteName = "Jane Doe Profile";
            body = "Jane Doe is a Principal Infrastructure Engineer specializing in resilient cloud platforms and distributed systems.";
        } else if (lowerUrl.contains("example") || lowerUrl.contains("linkedin.com/company/example")) {
            title = "[MOCK] Example Inc. - Official Homepage";
            description = "Example Inc. is a technology organization providing intelligence and data enrichment solutions.";
            siteName = "Example Inc.";
            body = "Example Inc. is a technology organization providing intelligence and data enrichment solutions for enterprise clients.";
        }

        String mockHtml = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <title>%s</title>
                    <meta name="description" content="%s" />
                    <meta property="og:title" content="%s" />
                    <meta property="og:site_name" content="%s" />
                </head>
                <body>
                    <h1>%s</h1>
                    <p>%s</p>
                </body>
                </html>
                """, title, description, title, siteName, title, body);

        return FetchedContent.success(url, 200, "text/html", mockHtml);
    }
}
