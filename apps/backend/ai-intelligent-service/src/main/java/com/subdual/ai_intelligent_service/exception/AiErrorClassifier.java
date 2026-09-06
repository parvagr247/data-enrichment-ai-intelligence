package com.subdual.ai_intelligent_service.exception;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strongly-typed classifier for AI / LLM provider errors and exceptions.
 * Identifies root causes (auth failure, unsupported model, rate limit, timeout, network failure, etc.),
 * determines whether an error is transient/retryable, and safely sanitizes messages to avoid leaking credentials.
 */
public final class AiErrorClassifier {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(key|token|auth|password|secret|api[_-]?key)[=:\\s]+([A-Za-z0-9_.-]{8,})"
    );

    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile(
            "(?i)\\b(?:status(?:_code)?|http|code)?\\s*[:=]?\\s*(?:400|401|403|404|408|429|500|502|503|504)\\b"
    );

    private AiErrorClassifier() {}

    public enum ErrorCategory {
        CONFIGURATION_ERROR(false),
        AUTHENTICATION_FAILURE(false),
        UNSUPPORTED_MODEL(false),
        RATE_LIMIT(true),
        TIMEOUT(true),
        NETWORK_FAILURE(true),
        SERVER_ERROR(true),
        STRUCTURED_PARSING_FAILURE(false),
        VALIDATION_FAILURE(false),
        UNKNOWN_PROVIDER_ERROR(false);

        private final boolean retryable;

        ErrorCategory(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    public record AiClassification(
            ErrorCategory category,
            int httpStatusCode,
            String sanitizedMessage,
            boolean isRetryable
    ) {}

    public static AiClassification classify(Throwable throwable) {
        if (throwable == null) {
            return new AiClassification(ErrorCategory.UNKNOWN_PROVIDER_ERROR, 0, "Unknown AI error", false);
        }

        int httpStatus = extractHttpStatus(throwable);
        String allMessages = collectAllMessages(throwable).toLowerCase(Locale.ROOT);
        String topMessage = sanitize(extractPrimaryMessage(throwable));

        // 1. Authentication & Authorization (401 / 403)
        if (httpStatus == 401 || httpStatus == 403
                || allMessages.contains("api_key_invalid")
                || allMessages.contains("invalid api key")
                || allMessages.contains("permission_denied")
                || allMessages.contains("unauthenticated")) {
            return new AiClassification(ErrorCategory.AUTHENTICATION_FAILURE, httpStatus > 0 ? httpStatus : 401, topMessage, false);
        }

        // 2. Unsupported / Deprecated Model (404)
        if (httpStatus == 404
                || allMessages.contains("is no longer available")
                || allMessages.contains("not found")
                || (allMessages.contains("models/gemini") && allMessages.contains("found"))) {
            return new AiClassification(ErrorCategory.UNSUPPORTED_MODEL, httpStatus > 0 ? httpStatus : 404, topMessage, false);
        }

        // 3. Rate Limit / Quota Exceeded (429)
        if (httpStatus == 429
                || allMessages.contains("resource_exhausted")
                || allMessages.contains("quota exceeded")
                || allMessages.contains("rate limit")
                || allMessages.contains("too many requests")) {
            return new AiClassification(ErrorCategory.RATE_LIMIT, httpStatus > 0 ? httpStatus : 429, topMessage, true);
        }

        // 4. Timeout
        if (httpStatus == 408
                || hasMatchingException(throwable, "SocketTimeoutException", "TimeoutException")
                || allMessages.contains("timeout")
                || allMessages.contains("timed out")) {
            return new AiClassification(ErrorCategory.TIMEOUT, httpStatus > 0 ? httpStatus : 408, topMessage, true);
        }

        // 5. Network / Connection Failures
        if (hasMatchingException(throwable, "ConnectException", "ResourceAccessException", "UnknownHostException", "HttpHostConnectException")
                || allMessages.contains("connection refused")
                || allMessages.contains("connection reset")
                || allMessages.contains("failed to connect")) {
            return new AiClassification(ErrorCategory.NETWORK_FAILURE, httpStatus, topMessage, true);
        }

        // 6. Upstream Server Errors (5xx)
        if (httpStatus >= 500 && httpStatus <= 599) {
            return new AiClassification(ErrorCategory.SERVER_ERROR, httpStatus, topMessage, true);
        }

        // 7. Structured JSON Parsing Failure
        if (hasMatchingException(throwable, "JsonProcessingException", "JsonParseException", "JsonMappingException")
                || allMessages.contains("failed to parse")
                || allMessages.contains("cannot deserialize")) {
            return new AiClassification(ErrorCategory.STRUCTURED_PARSING_FAILURE, httpStatus, topMessage, false);
        }

        return new AiClassification(ErrorCategory.UNKNOWN_PROVIDER_ERROR, httpStatus, topMessage, false);
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = SENSITIVE_PATTERN.matcher(raw).replaceAll("$1=[REDACTED]");
        return cleaned.replaceAll("(?i)(?:AIza|AQ\\.)[A-Za-z0-9_-]{15,}", "[REDACTED_API_KEY]");
    }

    private static int extractHttpStatus(Throwable throwable) {
        Throwable curr = throwable;
        while (curr != null) {
            if (curr instanceof HttpStatusCodeException hsce) {
                return hsce.getStatusCode().value();
            }
            if (curr instanceof RestClientResponseException rcre) {
                return rcre.getStatusCode().value();
            }
            String msg = curr.getMessage();
            if (msg != null) {
                Matcher m = HTTP_STATUS_PATTERN.matcher(msg);
                if (m.find()) {
                    String match = m.group().replaceAll("[^0-9]", "");
                    if (!match.isBlank()) {
                        try {
                            return Integer.parseInt(match);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            curr = curr.getCause();
        }
        return 0;
    }

    private static String collectAllMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable curr = throwable;
        int depth = 0;
        while (curr != null && depth < 10) {
            sb.append(curr.getClass().getName()).append(": ");
            if (curr.getMessage() != null) {
                sb.append(curr.getMessage()).append(" ");
            }
            curr = curr.getCause();
            depth++;
        }
        return sb.toString();
    }

    private static String extractPrimaryMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root.getMessage() != null && !root.getMessage().isBlank()) {
            return root.getClass().getSimpleName() + ": " + root.getMessage();
        }
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

    private static boolean hasMatchingException(Throwable throwable, String... simpleNames) {
        Throwable curr = throwable;
        int depth = 0;
        while (curr != null && depth < 10) {
            String name = curr.getClass().getSimpleName();
            for (String target : simpleNames) {
                if (name.equals(target)) {
                    return true;
                }
            }
            curr = curr.getCause();
            depth++;
        }
        return false;
    }
}
