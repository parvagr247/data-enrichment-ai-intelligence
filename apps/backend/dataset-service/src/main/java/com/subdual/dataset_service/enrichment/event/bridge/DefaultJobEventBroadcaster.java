package com.subdual.dataset_service.enrichment.event.bridge;

import com.subdual.dataset_service.enrichment.api.dto.response.ExecutionEvent;
import com.subdual.dataset_service.enrichment.model.JobState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;

/**
 * GoF Bridge Pattern - Refined Abstraction:
 * Coordinates domain-level event compilation and delegates the physical transmission
 * across the EventDeliveryChannel bridge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultJobEventBroadcaster implements JobEventBroadcaster {

    private final EventDeliveryChannel deliveryChannel;

    @Override
    public void broadcastExecutionEvent(
            String jobId,
            String rowId,
            int rowIndex,
            String entity,
            String status,
            String stage,
            String workerId,
            String message,
            Map<String, Object> metadata
    ) {
        if (jobId == null) {
            return;
        }

        ExecutionEvent event = new ExecutionEvent(
                jobId, rowId, rowIndex, entity, status, stage, workerId, message, Instant.now().toString(), metadata
        );

        deliveryChannel.deliverEvent(jobId, event);
    }

    @Override
    public void broadcastJobCompleted(String jobId, String status, int completed, int failed, Long durationMs) {
        if (jobId == null) {
            return;
        }
        deliveryChannel.deliverCompletion(jobId, status, completed, failed, durationMs);
    }

    @Override
    public SseEmitter subscribe(String jobId, JobState state, String userId, int concurrency) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10 minutes timeout

        if (state == null || (userId != null && !userId.isBlank() && state.userId != null && !state.userId.equals(userId))) {
            deliveryChannel.deliverError(emitter, "Job not found or access denied: " + jobId);
            return emitter;
        }

        if (deliveryChannel instanceof SseEventDeliveryChannel sseChannel) {
            Runnable cleanup = () -> sseChannel.removeSubscriber(jobId, emitter);
            deliveryChannel.registerSubscriber(jobId, emitter, cleanup);

            try {
                Map<String, Object> handshake = Map.of(
                        "jobId", jobId,
                        "datasetName", state.datasetName != null ? state.datasetName : "",
                        "concurrency", concurrency,
                        "totalRows", state.totalRows,
                        "completedRows", state.completedRows.get(),
                        "failedRows", state.failedRows.get(),
                        "status", state.status
                );
                deliveryChannel.sendHandshake(emitter, jobId, handshake);
                deliveryChannel.replayHistory(jobId, emitter);

                if (isTerminalState(state.status)) {
                    deliveryChannel.deliverCompletion(jobId, state.status, state.completedRows.get(), state.failedRows.get(), state.durationMs);
                    cleanup.run();
                }
            } catch (Exception ex) {
                log.warn("Error establishing SSE subscriber handshake for job {}: {}", jobId, ex.getMessage());
                cleanup.run();
            }
        }

        return emitter;
    }

    private boolean isTerminalState(String status) {
        return "COMPLETED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
    }
}
