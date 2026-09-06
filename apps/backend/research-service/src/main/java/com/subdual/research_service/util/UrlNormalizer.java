package com.subdual.research_service.util;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reusable URL normalization utility handling protocol, subdomains (such as international LinkedIn variants),
 * trailing slashes, tracking query parameters, and encoding.
 */
public final class UrlNormalizer {

    private static final Set<String> TRACKING_EXACT = Set.of(
            "fbclid", "gclid", "mc_eid", "_ga", "_gl", "source", "feature"
    );

    private static final Pattern LINKEDIN_SUBDOMAIN_PATTERN =
            Pattern.compile("^[a-z]{2,3}\\.linkedin\\.com$", Pattern.CASE_INSENSITIVE);

    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile("^\\[.*?\\]\\((https?://[^\\s\\)]+|[^\\s\\)]+)\\)$", Pattern.CASE_INSENSITIVE);

    private UrlNormalizer() {}

    /**
     * Unwraps Markdown links [label](url), angle brackets <url>, square brackets [url], or quotes.
     */
    public static String unwrapLink(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String input = raw.trim();

        // 1. Markdown link pattern: [label](targetUrl) or [targetUrl](targetUrl)
        java.util.regex.Matcher m = MARKDOWN_LINK_PATTERN.matcher(input);
        if (m.matches()) {
            input = m.group(1).trim();
        }

        // 2. Enclosed in quotes: "url" or 'url'
        if ((input.startsWith("\"") && input.endsWith("\"")) || (input.startsWith("'") && input.endsWith("'"))) {
            if (input.length() >= 2) {
                input = input.substring(1, input.length() - 1).trim();
            }
        }

        // 3. Enclosed in angle brackets: <url>
        if (input.startsWith("<") && input.endsWith(">")) {
            if (input.length() >= 2) {
                input = input.substring(1, input.length() - 1).trim();
            }
        }

        // 4. Enclosed in square brackets without markdown parentheses: [url]
        if (input.startsWith("[") && input.endsWith("]")) {
            if (input.length() >= 2) {
                input = input.substring(1, input.length() - 1).trim();
            }
        }

        // 5. Remove any remaining stray leading/trailing brackets, quotes, or parens
        input = input.replaceAll("^[\\[\\(<\"']+", "").replaceAll("[\\]\\)>\"']+$", "").trim();

        return input;
    }

    /**
     * Normalizes a URL:
     * - unwraps Markdown links [label](url), brackets, and quotes
     * - defaults missing scheme to https
     * - normalizes scheme to lowercase (http/https)
     * - strips www. and international LinkedIn country subdomains (e.g. in.linkedin.com -> linkedin.com)
     * - removes default ports (:80, :443)
     * - trims trailing slash for non-root paths
     * - removes tracking query parameters (utm_*, ref_*, fbclid, etc.)
     * - sorts remaining query parameters
     * - strips fragment (#...)
     * - guarantees idempotency: normalize(normalize(url)).equals(normalize(url))
     */
    public static String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }

        String input = unwrapLink(rawUrl);
        if (input.isBlank()) {
            return "";
        }

        int hashIdx = input.indexOf('#');
        if (hashIdx >= 0) {
            input = input.substring(0, hashIdx);
        }

        input = input.replace(" ", "%20");

        if (!input.contains("://")) {
            input = "https://" + input;
        }

        try {
            URI uri = URI.create(input);
            String scheme = "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            host = normalizeHost(host);

            String portPart = formatPortPart(scheme, uri.getPort());
            String path = normalizePath(uri.getRawPath());
            String query = cleanQueryParameters(uri.getRawQuery());

            return scheme + "://" + host + portPart + path + query;
        } catch (Exception e) {
            return input;
        }
    }

    public static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String cleanHost = host.toLowerCase(Locale.ROOT);
        if (cleanHost.startsWith("www.")) {
            cleanHost = cleanHost.substring(4);
        }
        if (LINKEDIN_SUBDOMAIN_PATTERN.matcher(cleanHost).matches()) {
            cleanHost = "linkedin.com";
        }
        return cleanHost;
    }

    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        path = path.replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String formatPortPart(String scheme, int port) {
        boolean isDefaultPort = port == -1
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return isDefaultPort ? "" : ":" + port;
    }

    public static boolean isLinkedInInternational(String host) {
        if (host == null || host.isBlank()) return false;
        String cleanHost = host.toLowerCase(Locale.ROOT);
        if (cleanHost.startsWith("www.")) cleanHost = cleanHost.substring(4);
        return LINKEDIN_SUBDOMAIN_PATTERN.matcher(cleanHost).matches();
    }

    public static String cleanQueryParameters(String rawQuery) {
        return cleanQueryParameters(rawQuery, false);
    }

    public static String cleanQueryParameters(String rawQuery, boolean stripRef) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        String cleanParams = Arrays.stream(rawQuery.split("&"))
                .filter(param -> !param.isBlank())
                .filter(param -> {
                    String key = param.split("=")[0].toLowerCase(Locale.ROOT);
                    if (key.startsWith("utm_") || key.startsWith("ref_")) {
                        return false;
                    }
                    if (stripRef && "ref".equals(key)) {
                        return false;
                    }
                    return !TRACKING_EXACT.contains(key);
                })
                .sorted()
                .collect(Collectors.joining("&"));

        return cleanParams.isBlank() ? "" : "?" + cleanParams;
    }

    /**
     * Compares two URLs for equivalence, ignoring protocol, www/country subdomains, trailing slashes, and tracking query params.
     */
    public static boolean isSameUrl(String u1, String u2) {
        if (u1 == null || u2 == null) {
            return false;
        }
        String n1 = toComparisonKey(u1);
        String n2 = toComparisonKey(u2);
        return !n1.isEmpty() && n1.equalsIgnoreCase(n2);
    }

    public static String toComparisonKey(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String normalized = normalize(rawUrl);
        return normalized.replaceFirst("^https?://", "")
                .replaceFirst("/+$", "");
    }

    public static String extractDomain(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        try {
            String normalized = normalize(rawUrl);
            URI uri = URI.create(normalized);
            return uri.getHost() != null ? uri.getHost() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
