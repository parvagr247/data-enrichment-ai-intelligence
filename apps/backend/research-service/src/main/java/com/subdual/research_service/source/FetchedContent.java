package com.subdual.research_service.source;

/**
 * Result of a remote content fetch operation.
 */
public record FetchedContent(
        String url,
        int httpStatus,
        String contentType,
        String rawBody,
        boolean success,
        String errorMessage
) {
    public static FetchedContent success(String url, int status, String contentType, String body) {
        return new FetchedContent(url, status, contentType, body, true, null);
    }

    public static FetchedContent failed(String url, int status, String errorMessage) {
        return new FetchedContent(url, status, null, null, false, errorMessage);
    }
}
