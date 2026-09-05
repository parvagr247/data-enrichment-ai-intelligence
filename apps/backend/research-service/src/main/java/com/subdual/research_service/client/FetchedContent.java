package com.subdual.research_service.client;

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
