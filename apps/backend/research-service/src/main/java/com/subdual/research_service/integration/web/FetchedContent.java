package com.subdual.research_service.integration.web;

public record FetchedContent(
        String url,
        int statusCode,
        String contentType,
        String body,
        boolean success,
        String errorMessage
) {
    public static FetchedContent success(String url, int statusCode, String contentType, String body) {
        return new FetchedContent(url, statusCode, contentType, body, true, null);
    }

    public static FetchedContent successful(String url, int statusCode, String contentType, String body) {
        return success(url, statusCode, contentType, body);
    }

    public static FetchedContent failed(String url, int statusCode, String errorMessage) {
        return new FetchedContent(url, statusCode, null, null, false, errorMessage);
    }

    public static FetchedContent failure(String url, int statusCode, String errorMessage) {
        return failed(url, statusCode, errorMessage);
    }

    public String rawBody() {
        return body;
    }

    public int httpStatus() {
        return statusCode;
    }
}
