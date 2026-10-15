package com.subdual.dataset_service.enrichment.event;

import com.subdual.dataset_service.enrichment.model.JobState;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * GoF Bridge Pattern - Abstraction Interface:
 * High-level domain contract for broadcasting dataset enrichment execution progress,
 * worker transitions, and terminal completion states. Decouples domain job orchestration
 * from specific transmission transports.
 */
public interface JobEventBroadcaster {

    void broadcastExecutionEvent(
            String jobId,
            String rowId,
            int rowIndex,
            String entity,
            String status,
            String stage,
            String workerId,
            String message,
            Map<String, Object> metadata
    );

    void broadcastJobCompleted(String jobId, String status, int completed, int failed, Long durationMs);

    SseEmitter subscribe(String jobId, JobState state, String userId, int concurrency);
}
