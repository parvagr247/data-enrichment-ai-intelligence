package com.subdual.ai_intelligent_service.dto;

/**
 * Telemetry and token usage diagnostics for AI invocations (Tasks 79, 80).
 */
public record AiExecutionMetrics(
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long executionTimeMs,
        int callCount,
        int retryCount,
        String status
) {
    public static AiExecutionMetrics deterministic(long durationMs) {
        return new AiExecutionMetrics("deterministic-engine", 0, 0, 0, durationMs, 0, 0, "SUCCESS");
    }

    public static AiExecutionMetrics fallback(String reason, long durationMs, int retries) {
        return new AiExecutionMetrics("deterministic-fallback: " + reason, 0, 0, 0, durationMs, retries, retries, "FALLBACK");
    }
}
