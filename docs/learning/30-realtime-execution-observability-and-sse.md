# Concept 30: Real-Time Execution Observability, Server-Sent Events, and Dynamic Concurrency Visualization

When background batch systems scale from single-threaded prototypes to concurrent thread pools, user trust depends directly on **execution observability**.

A generic progress indicator ("Processing 0 of 20 records...") hides concurrency, obscures long-running API latencies, and makes parallel execution feel sequential and opaque.

This guide explains how the platform achieves real-time execution observability using **Server-Sent Events (SSE)**, thread-safe event streaming, normalized client-side state, and dynamic concurrency visualization.

---

## Why This Exists

1. **The Opaqueness of Batch Systems**:
   If a user submits 20 rows and sees a static progress bar slowly ticking from 0 to 100%, they cannot tell whether 3 workers are running in parallel, whether a row is hung on web scraping, or whether AI synthesis is actively generating attributes.
2. **Aggressive Polling Pitfalls**:
   Polling `GET /api/v1/enrichment/jobs/{id}` every 500ms creates wasteful HTTP overhead, clutters server logs, introduces polling jitter, and risks race conditions.
3. **The Overhead of WebSockets**:
   Batch progress is strictly **unidirectional** (backend to frontend). Introducing full-duplex WebSockets requires complex handshake negotiations, separate port/protocol routing through reverse proxies, and ping/pong heartbeats for what is fundamentally an HTTP event stream.

---

## Core Architecture: Server-Sent Events (SSE)

Server-Sent Events (SSE) provide a lightweight, standard W3C protocol (`text/event-stream`) for pushing real-time updates from server to client over persistent HTTP/1.1 or HTTP/2 connections.

```
Frontend (Next.js / Browser)                 Dataset Service (:9743)
┌────────────────────────────┐              ┌──────────────────────────────────────────────┐
│ EventSource connection     │ ───────────> │ GET /api/v1/enrichment/jobs/{id}/events      │
│                            │              │                                              │
│ onInit(config)             │ <─────────── │ event: init (concurrency=3, total=20)        │
│                            │              │                                              │
│ onEvent(executionEvent)    │ <─────────── │ event: execution-event (Row #1 -> RESEARCH)  │
│ [Worker 1 card updates]    │              │                                              │
│                            │              │                                              │
│ onEvent(executionEvent)    │ <─────────── │ event: execution-event (Row #2 -> AI)        │
│ [Worker 2 card updates]    │              │                                              │
│                            │              │                                              │
│ onEvent(executionEvent)    │ <─────────── │ event: execution-event (Row #1 -> COMPLETED) │
│ [Appends to Completed list]│              │                                              │
│                            │              │                                              │
│ onJobCompleted(stats)      │ <─────────── │ event: job-completed (durationMs=14200)      │
└────────────────────────────┘              └──────────────────────────────────────────────┘
```

---

## The Execution Event Contract

To preserve strict microservice decoupling and provider agnosticism, the frontend **never** knows about Java thread pools, `ExecutorService` internals, Tavily search queries, or Spring AI prompts.

The backend translates internal operations into a generic, displayable `ExecutionEvent`:

```json
{
  "jobId": "d8dfa97a-b1e2-4103-ac71-013252a3a824",
  "rowId": "row-0",
  "rowIndex": 0,
  "entity": "Jasveer Singh",
  "status": "PROCESSING",
  "stage": "RESEARCH",
  "workerId": "worker-1",
  "message": "Searching public sources & discovering references...",
  "timestamp": "2026-09-06T09:20:00.123Z",
  "metadata": {
    "sourcesCount": 4,
    "confidence": 0.92,
    "attributesCount": 6
  }
}
```

### Supported Execution Lifecycle Stages
1. `QUEUED`: Row is registered in memory, awaiting an available worker thread.
2. `RESEARCH`: Assigned worker is executing search discovery and polite content fetching.
3. `AI_EXTRACTION`: Multi-source facts are being synthesized and validated via LLM.
4. `PERSISTENCE`: Enriched entity profile is being written to relational persistence.
5. `COMPLETED` / `PARTIAL`: Row enrichment finished; enriched attributes and sources are ready.
6. `FAILED`: Row encountered an isolated failure (e.g. missing identifier or upstream timeout).

---

## Backend Implementation: Spring WebMvc `SseEmitter`

### 1. Concurrent Emitter Registry & Replay Buffer
When a browser client connects (or reconnects after a momentary network blip), it must not lose past events. The service maintains an active emitter list and a bounded event history:

```java
private final Map<String, List<SseEmitter>> jobEmitters = new ConcurrentHashMap<>();
private final Map<String, List<ExecutionEvent>> jobEventHistory = new ConcurrentHashMap<>();

@Override
public SseEmitter subscribeJobEvents(String jobId) {
    JobState state = activeJobs.get(jobId);
    SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10 min timeout

    // Register cleanup callbacks
    Runnable cleanup = () -> {
        List<SseEmitter> list = jobEmitters.get(jobId);
        if (list != null) list.remove(emitter);
    };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());

    jobEmitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);

    // Replay handshake & historical events
    emitter.send(SseEmitter.event().name("init").data(Map.of(
        "jobId", jobId,
        "concurrency", enrichmentTaskExecutor.getConcurrency(),
        "totalRows", state.totalRows
    )));

    List<ExecutionEvent> history = jobEventHistory.get(jobId);
    if (history != null) {
        synchronized (history) {
            for (ExecutionEvent ev : history) {
                emitter.send(SseEmitter.event().name("execution-event").data(ev));
            }
        }
    }
    return emitter;
}
```

### 2. Thread-Safe Emission from Bounded Worker Threads
As worker threads in `EnrichmentTaskExecutor` process rows, they broadcast events safely across all active emitters:

```java
private void emitExecutionEvent(String jobId, String rowId, int rowIndex, String entity,
                                String status, String stage, String workerId,
                                String message, Map<String, Object> metadata) {
    ExecutionEvent event = new ExecutionEvent(
        jobId, rowId, rowIndex, entity, status, stage, workerId, message,
        Instant.now().toString(), metadata
    );

    // Record into bounded history (up to 500 events per job)
    List<ExecutionEvent> history = jobEventHistory.computeIfAbsent(jobId, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (history) {
        if (history.size() >= 500) history.remove(0);
        history.add(event);
    }

    // Push to connected clients
    List<SseEmitter> emitters = jobEmitters.get(jobId);
    if (emitters != null) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("execution-event").data(event));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
    }
}
```

---

## Frontend State Normalization & Concurrency UI

### 1. Single Source of Truth (`LiveExecutionState`)
Instead of scattered boolean flags and arbitrary timers, the frontend maintains a normalized execution store:

```ts
export interface LiveExecutionState {
  job: {
    id: string;
    status: string;
    total: number;
    completed: number;
    failed: number;
    remaining: number;
    concurrency: number;
  };
  rows: Record<string, RowState>;
  activeWorkers: Record<string, ActiveWorkerState>;
  activityLog: ExecutionLogEntry[];
}
```

### 2. Dynamic Worker Slot Virtualization
The UI does **not** hardcode "Worker 1, Worker 2, Worker 3".
It reads `concurrency` directly from the backend `init` event. If the backend is configured with concurrency 5, the frontend automatically generates 5 worker slots:
- Active workers render with animated gradient headers, live stage badges, discovered source indicators, and live elapsed duration timers.
- Idle workers render as clean waiting cards awaiting queue item assignment.

### 3. Real-Time Incremental Completed Row Streaming
The user never has to wait for an entire 50-row batch to finish before seeing results.
As each row finishes:
- Its `COMPLETED` event contains the full `RowEnrichmentResult` payload.
- The row is immediately added to the "Recently Completed" list with attribute counts, source counts, and confidence scores.
- The user can click **"Inspect Evidence"** to open the multi-tab evidence modal **while other workers are still actively running!**

### 4. Isolated Failure Reporting
When an individual row fails (e.g., DNS error, missing identifier, or downstream timeout):
- That row is marked `FAILED` with a human-readable explanation.
- The failure is isolated to the "Failed Rows" panel.
- The remaining rows and active workers continue running completely unaffected.

---

## Summary of Benefits

| Aspect | Legacy Status Approach | Live Observability (SSE) |
| :--- | :--- | :--- |
| **User Transparency** | "Processing 0 of 20..." (opaque) | Live cards showing active worker slots, stages, and discovered sources |
| **Communication Protocol** | Aggressive 1s HTTP polling | One-way persistent HTTP `text/event-stream` (SSE) with graceful polling fallback |
| **Result Availability** | All results withheld until batch completion | Incremental streaming; completed rows can be inspected immediately |
| **Concurrency Truthfulness** | Hardcoded assumption | Dynamically bound to backend thread pool configuration (`concurrency`) |
| **Failure Handling** | Opaque error counter | Isolated row failure panel with human-readable error reasons |
