package com.subdual.dataset_service.enrichment.event;

import com.subdual.dataset_service.enrichment.dto.ExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GoF Bridge Pattern - Concrete Implementor:
 * Encapsulates Spring WebMvc SseEmitter connections, lifecycle pruning,
 * in-memory ring-buffer replay history, and HTTP event stream transmission.
 */
@Component
@Slf4j
public class SseEventDeliveryChannel implements EventDeliveryChannel {

    private final Map<String, List<SseEmitter>> jobEmitters = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionEvent>> jobEventHistory = new ConcurrentHashMap<>();

    @Override
    public void deliverEvent(String jobId, ExecutionEvent event) {
        recordEventHistory(jobId, event);
        broadcastToEmitters(jobId, event);
    }

    @Override
    public void deliverCompletion(String jobId, String status, int completed, int failed, Long durationMs) {
        List<SseEmitter> emitters = jobEmitters.get(jobId);
        if (emitters != null && !emitters.isEmpty()) {
            Map<String, Object> data = Map.of(
                    "jobId", jobId,
                    "status", status,
                    "completedRows", completed,
                    "failedRows", failed,
                    "durationMs", durationMs != null ? durationMs : 0L
            );
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("job-completed").data(data));
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
            jobEmitters.remove(jobId);
        }
    }

    @Override
    public void deliverError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void registerSubscriber(String jobId, SseEmitter emitter, Runnable onCleanup) {
        jobEmitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(onCleanup);
        emitter.onTimeout(() -> {
            emitter.complete();
            onCleanup.run();
        });
        emitter.onError(e -> onCleanup.run());
    }

    @Override
    public void replayHistory(String jobId, SseEmitter emitter) throws Exception {
        List<ExecutionEvent> history = jobEventHistory.get(jobId);
        if (history != null) {
            synchronized (history) {
                for (ExecutionEvent ev : history) {
                    emitter.send(SseEmitter.event().name("execution-event").data(ev));
                }
            }
        }
    }

    @Override
    public void sendHandshake(SseEmitter emitter, String jobId, Map<String, Object> handshakeData) throws Exception {
        emitter.send(SseEmitter.event().name("init").data(handshakeData));
    }

    public void removeSubscriber(String jobId, SseEmitter emitter) {
        List<SseEmitter> list = jobEmitters.get(jobId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    private void recordEventHistory(String jobId, ExecutionEvent event) {
        List<ExecutionEvent> history = jobEventHistory.computeIfAbsent(
                jobId, k -> Collections.synchronizedList(new ArrayList<>())
        );
        synchronized (history) {
            if (history.size() >= 500) {
                history.remove(0);
            }
            history.add(event);
        }
    }

    private void broadcastToEmitters(String jobId, ExecutionEvent event) {
        List<SseEmitter> emitters = jobEmitters.get(jobId);
        if (emitters != null && !emitters.isEmpty()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("execution-event").data(event));
                } catch (Exception ex) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }
}
