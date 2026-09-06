package com.subdual.ai_intelligent_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class AiErrorClassifierTest {

    @Test
    void testClassifyAuthenticationFailure() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatusCode.valueOf(401), "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        AiErrorClassifier.AiClassification result = AiErrorClassifier.classify(ex);

        assertEquals(AiErrorClassifier.ErrorCategory.AUTHENTICATION_FAILURE, result.category());
        assertEquals(401, result.httpStatusCode());
        assertFalse(result.isRetryable());
    }

    @Test
    void testClassifyUnsupportedModel() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatusCode.valueOf(404), "This model models/gemini-2.0-flash is no longer available", HttpHeaders.EMPTY, new byte[0], null);
        AiErrorClassifier.AiClassification result = AiErrorClassifier.classify(ex);

        assertEquals(AiErrorClassifier.ErrorCategory.UNSUPPORTED_MODEL, result.category());
        assertEquals(404, result.httpStatusCode());
        assertFalse(result.isRetryable());
    }

    @Test
    void testClassifyRateLimit() {
        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatusCode.valueOf(429), "RESOURCE_EXHAUSTED: quota exceeded", HttpHeaders.EMPTY, new byte[0], null);
        AiErrorClassifier.AiClassification result = AiErrorClassifier.classify(ex);

        assertEquals(AiErrorClassifier.ErrorCategory.RATE_LIMIT, result.category());
        assertEquals(429, result.httpStatusCode());
        assertTrue(result.isRetryable());
    }

    @Test
    void testClassifyTimeout() {
        SocketTimeoutException ex = new SocketTimeoutException("Read timed out after 5000ms");
        AiErrorClassifier.AiClassification result = AiErrorClassifier.classify(ex);

        assertEquals(AiErrorClassifier.ErrorCategory.TIMEOUT, result.category());
        assertTrue(result.isRetryable());
    }

    @Test
    void testClassifyNetworkFailure() {
        ConnectException ex = new ConnectException("Connection refused to generativelanguage.googleapis.com");
        AiErrorClassifier.AiClassification result = AiErrorClassifier.classify(ex);

        assertEquals(AiErrorClassifier.ErrorCategory.NETWORK_FAILURE, result.category());
        assertTrue(result.isRetryable());
    }

    @Test
    void testClassifyServerError() {
        HttpServerErrorException ex = HttpServerErrorException.create(
                HttpStatusCode.valueOf(503), "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
        AiErrorClassifier.AiClassification result = AiErrorClassifier.classify(ex);

        assertEquals(AiErrorClassifier.ErrorCategory.SERVER_ERROR, result.category());
        assertEquals(503, result.httpStatusCode());
        assertTrue(result.isRetryable());
    }

    @Test
    void testSanitizesSecretTokens() {
        String leaked = "Error connecting with key=AQ.Ab8RN6KOO7X3cVnexNwGqqQmEYyz7fGlxQfBHUTrxrl98L3ewA or AIzaSyAbcdefgh12345678";
        String sanitized = AiErrorClassifier.sanitize(leaked);

        assertFalse(sanitized.contains("AQ.Ab8RN6KOO7X3cVnexNwGqqQmEYyz7fGlxQfBHUTrxrl98L3ewA"));
        assertFalse(sanitized.contains("AIzaSyAbcdefgh12345678"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }
}
