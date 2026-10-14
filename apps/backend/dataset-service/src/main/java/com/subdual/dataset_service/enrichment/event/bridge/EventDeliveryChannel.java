package com.subdual.dataset_service.enrichment.event.bridge;

import com.subdual.dataset_service.enrichment.api.dto.response.ExecutionEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * GoF Bridge Pattern - Implementor Interface:
 * Declares the low-level transmission primitives for delivering execution events,
 * completion summaries, and client reconnection history across concrete transport media
 * (e.g., Server-Sent Events, WebSockets, or Message Brokers).
 */
public interface EventDeliveryChannel {

    void deliverEvent(String jobId, ExecutionEvent event);

    void deliverCompletion(String jobId, String status, int completed, int failed, Long durationMs);

    void deliverError(SseEmitter emitter, String message);

    void registerSubscriber(String jobId, SseEmitter emitter, Runnable onCleanup);

    void replayHistory(String jobId, SseEmitter emitter) throws Exception;

    void sendHandshake(SseEmitter emitter, String jobId, Map<String, Object> handshakeData) throws Exception;
}
