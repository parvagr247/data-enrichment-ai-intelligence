package com.subdual.research_service.integration.web;

import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.WebFetchProperties;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DefaultWebContentFetcher implements WebContentFetcher {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "mysql", "enrichment-mysql", "ai-intelligent-service",
            "dataset-service", "host.docker.internal"
    );

    private final WebFetchProperties properties;
    private final HttpClient httpClient;
    private final boolean mockMode;

    public DefaultWebContentFetcher(WebFetchProperties properties, ResearchDiscoveryProperties discoveryProperties) {
        this.properties = properties;
        this.mockMode = discoveryProperties != null && "mock".equalsIgnoreCase(discoveryProperties.provider());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties != null ? properties.connectTimeoutMs() : 3000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public FetchedContent fetch(String url) {
        if (url == null || url.isBlank()) {
            return FetchedContent.failed(url, 400, "URL cannot be empty");
        }

        if (mockMode || isMockUrl(url)) {
            return generateMockContent(url);
        }

        return fetchRealContent(url);
    }

    private FetchedContent fetchRealContent(String url) {
        URI uri;
        try {
            uri = validateAndParseUri(url);
        } catch (IllegalArgumentException ex) {
            return FetchedContent.failed(url, 400, ex.getMessage());
        } catch (SecurityException ex) {
            return FetchedContent.failed(url, 403, ex.getMessage());
        }

        try {
            HttpRequest request = buildHttpRequest(uri);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return processHttpResponse(response, url);
        } catch (Exception ex) {
            log.warn("Error fetching URL '{}': {}", url, ex.getMessage());
            return FetchedContent.failed(url, 500, "Fetch failed: " + ex.getMessage());
        }
    }

    private URI validateAndParseUri(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception ex) {
            log.warn("Malformed URL during content fetch: '{}'", url);
            throw new IllegalArgumentException("Malformed URL: " + ex.getMessage(), ex);
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported URI scheme: " + scheme);
        }

        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        if (host.isBlank() || isBlockedHost(host)) {
            log.warn("Blocked fetch request to restricted host: '{}'", host);
            throw new SecurityException("Access to host is restricted: " + host);
        }

        return uri;
    }

    private HttpRequest buildHttpRequest(URI uri) {
        int timeoutMs = properties != null ? properties.readTimeoutMs() : 5000;
        String userAgent = properties != null && properties.userAgent() != null ? properties.userAgent() : "Mozilla/5.0";

        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,application/json;q=0.8")
                .GET()
                .build();
    }

    private FetchedContent processHttpResponse(HttpResponse<InputStream> response, String url) throws Exception {
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

        long maxBytes = (long) (properties != null ? properties.maxResponseSizeMb() : 5) * 1024 * 1024;
        String body = readBoundedBody(response.body(), maxBytes);

        return FetchedContent.success(url, statusCode, contentType, body);
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
                if (isPrivateIp(addr.getAddress())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Unresolvable host will naturally fail on connection
        }

        return false;
    }

    private boolean isPrivateIp(byte[] raw) {
        if (raw == null || raw.length != 4) {
            return false;
        }
        int b0 = raw[0] & 0xFF;
        int b1 = raw[1] & 0xFF;
        return (b0 == 127 || b0 == 10 || b0 == 0)
                || (b0 == 192 && b1 == 168)
                || (b0 == 169 && b1 == 254)
                || (b0 == 172 && (b1 >= 16 && b1 <= 31));
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

        if (lowerUrl.contains("linkedin.com") && !lowerUrl.contains("mock-linkedin-allow")) {
            log.info("[MOCK] Simulating LinkedIn HTTP 999 anti-bot response for '{}'", url);
            return FetchedContent.failed(url, 999, "HTTP 999: Request Denied");
        }

        MockData mock = resolveMockData(lowerUrl);

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
                """, mock.title(), mock.description(), mock.title(), mock.siteName(), mock.title(), mock.body());

        return FetchedContent.success(url, 200, "text/html", mockHtml);
    }

    private MockData resolveMockData(String lowerUrl) {
        if (lowerUrl.contains("spring-boot") || lowerUrl.contains("spring.io") || lowerUrl.contains("spring-projects")) {
            return new MockData(
                    "[MOCK] Spring Boot - Production-Grade Spring Applications",
                    "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications that you can just run.",
                    "Spring",
                    "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications that you can just run. It takes an opinionated view of the Spring platform."
            );
        }
        if (lowerUrl.contains("mnit.ac.in") || (lowerUrl.contains("parv-agrawal") && lowerUrl.contains("mnit"))) {
            return new MockData(
                    "Parv Agrawal - Department of Chemical Engineering - MNIT Jaipur",
                    "Parv Agrawal is a student of Chemical Engineering at Malaviya National Institute of Technology Jaipur.",
                    "MNIT Jaipur",
                    "Parv Agrawal is a student at Malaviya National Institute of Technology Jaipur. Role: Undergraduate Student. Organization: MNIT Jaipur. Department: Chemical Engineering. Location: Jaipur, Rajasthan, India. Graduated from MNIT Jaipur."
            );
        }
        if (lowerUrl.contains("deshaw") || lowerUrl.contains("iitd.ac.in")) {
            return new MockData(
                    "Parv Agrawal - Quantitative Analyst - D. E. Shaw",
                    "Parv Agrawal is a Quantitative Analyst at D. E. Shaw in Hyderabad.",
                    "D. E. Shaw",
                    "Parv Agrawal works as Quantitative Analyst at D. E. Shaw. Graduated from IIT Delhi in Computer Science. Location: Hyderabad, India."
            );
        }
        if (lowerUrl.contains("dentistry") || lowerUrl.contains("dr-jane-doe")) {
            return new MockData(
                    "Dr. Jane Doe, DDS - Family Dentistry & Dental Clinic",
                    "Dr. Jane Doe provides dental care, teeth whitening, and pediatric dentistry.",
                    "Downtown Dentistry Clinic",
                    "Dr. Jane Doe, DDS is a family dentist at Downtown Dental Clinic. Location: Chicago, IL. Specializes in oral hygiene and dental surgery."
            );
        }
        if (lowerUrl.contains("imdb") || lowerUrl.contains("actress") || lowerUrl.contains("cinema")) {
            return new MockData(
                    "Jane Doe - Actress Filmography and Credits",
                    "Jane Doe is a theater and film actress known for dramatic roles in independent movies.",
                    "Internet Movie Database",
                    "Jane Doe is an actress. Filmography includes award-winning short films and stage plays in Los Angeles, CA."
            );
        }
        if (lowerUrl.contains("alex-rivera")) {
            return new MockData(
                    "Alex Rivera - Senior Technical Recruiter at CloudScale Systems",
                    "Alex Rivera is a Senior Technical Recruiter at CloudScale Systems in San Francisco, CA.",
                    "CloudScale Systems",
                    "Alex Rivera is Senior Technical Recruiter at CloudScale Systems, based in San Francisco, CA. Role: Senior Technical Recruiter. Organization: CloudScale Systems. Location: San Francisco, CA. Skills: Technical Recruiting, Talent Sourcing, Candidate Screening. Activity: [AUTHORED] We are actively hiring backend interns and junior engineers for our Spring Boot platform at CloudScale Systems! DM me with your resume. Education: B.S. from University of California, Berkeley."
            );
        }
        if (lowerUrl.contains("sarah-chen") || lowerUrl.contains("fintechglobal")) {
            return new MockData(
                    "Dr. Sarah Chen - Staff Backend Architect at FinTech Global",
                    "Dr. Sarah Chen is Staff Backend Architect at FinTech Global in New York, NY.",
                    "FinTech Global",
                    "Dr. Sarah Chen is Staff Backend Architect at FinTech Global, based in New York, NY. Role: Staff Backend Architect. Organization: FinTech Global. Location: New York, NY. Education: Ph.D. in Computer Science from MIT. Skills: Java, Spring Boot, Microservices, Kubernetes, Docker, Distributed Systems. Activity: [AUTHORED] Designing High-Throughput Spring Boot 3 Microservices in Production: key architectural patterns and resilience strategies."
            );
        }
        if (lowerUrl.contains("marcus-vance") || lowerUrl.contains("devscale")) {
            return new MockData(
                    "Marcus Vance - Founder & CEO at DevScale AI",
                    "Marcus Vance is Founder and CEO at DevScale AI in Austin, TX.",
                    "DevScale AI",
                    "Marcus Vance is Founder and CEO at DevScale AI, based in Austin, TX. Role: Founder & CEO. Organization: DevScale AI. Location: Austin, TX. Education: B.S. in Computer Science from Stanford University. Skills: Distributed Systems, AI Infrastructure, Go, Python. Activity: [AUTHORED] Building scalable engineering teams and scaling our distributed data infrastructure. Always open to mentorship and hiring talented engineers."
            );
        }
        if (lowerUrl.contains("cloudscale") || (lowerUrl.contains("jane-doe") && lowerUrl.contains("team"))) {
            return new MockData(
                    "Jane Doe - Principal Infrastructure Engineer at CloudScale Systems",
                    "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems based in San Francisco, CA. Graduated from MIT. Profile: linkedin.com/in/jane-doe",
                    "CloudScale Systems",
                    "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems, based in San Francisco, CA. She graduated from MIT with a degree in Computer Science. Connect with Jane Doe on LinkedIn at linkedin.com/in/jane-doe."
            );
        }
        if (lowerUrl.contains("jane-doe")) {
            return new MockData(
                    "Jane Doe - Principal Infrastructure Engineer",
                    "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems based in San Francisco, CA. Graduated from MIT.",
                    "Jane Doe Profile",
                    "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems, based in San Francisco, CA. She graduated from MIT."
            );
        }
        if (lowerUrl.contains("example") || lowerUrl.contains("linkedin.com/company/example")) {
            return new MockData(
                    "[MOCK] Example Inc. - Official Homepage",
                    "Example Inc. is a technology organization providing intelligence and data enrichment solutions.",
                    "Example Inc.",
                    "Example Inc. is a technology organization providing intelligence and data enrichment solutions for enterprise clients."
            );
        }

        return new MockData(
                "[MOCK] Entity Overview and Reference",
                "This is deterministic mock description for automated pipeline testing.",
                "Mock System",
                "This is deterministic mock text used during testing and offline verification."
        );
    }

    private record MockData(String title, String description, String siteName, String body) {}
}
