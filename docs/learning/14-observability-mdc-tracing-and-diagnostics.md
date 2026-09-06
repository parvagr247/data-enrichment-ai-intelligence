# Concept 14: Observability, MDC Correlation Tracing & ProblemDetail Diagnostics

When an enterprise data enrichment platform scales across four microservices executing asynchronous worker threads and concurrent web scrapers, diagnosing operational failures becomes a formidable challenge. A single batch enrichment job might process 50 rows simultaneously; each row queries search engines, scrapes 3 external domains, and calls LLM inference.

If logs are unstructured and unkeyed, log entries from 50 concurrent research tasks interleave into an unreadable stream. Furthermore, if an external site blocks a scraper or an AI call times out, returning a generic `500 Internal Server Error` leaves clients with zero actionable diagnostic context.

This guide explains how this platform implements **end-to-end observability**, **SLF4J Mapped Diagnostic Context (MDC) correlation tracing**, **in-flight pipeline diagnostics**, and **RFC 7807 `ProblemDetail` error handling**.

---

## Why This Exists

In distributed research systems, multiple failure modes occur naturally on every run:
1. External websites block scrapers with anti-bot challenges (e.g. LinkedIn HTTP 999). The system must record that the source was skipped and continue with corroborating sources, rather than crashing.
2. In asynchronous background job execution, worker threads are pooled and reused. Without thread-local correlation tokens, developers cannot isolate the log output of a specific `jobId` or `entityId`.
3. Upstream UI clients need machine-readable error responses with standard HTTP semantics, distinguishing between client validation errors (`400 Bad Request`), business rule violations (`422 Unprocessable Entity`), and downstream provider outages (`502 Bad Gateway`).

---

## The Problem

A naive implementation typically suffers from:
* **The Interleaved Log Nightmare**: Using plain `log.info("Processing target: " + name)`. When 20 threads execute concurrently, the console logs interleave randomly. Isolating the log trajectory of a single failed row requires painful manual timestamp correlation.
* **MDC Thread Contamination**: Putting a correlation key into MDC (`MDC.put("jobId", id)`) without wrapping execution in a `try-finally` block. Because worker threads are pooled and reused by the JVM, the next task running on that thread inherits the previous job's ID, producing corrupt log traces.
* **Opaque Bespoke Error Payloads**: Creating custom error classes (`{"success": false, "err": "msg"}`). Every API client, frontend component, and API gateway must write bespoke parsing logic for every service's proprietary error format.
* **Silent Dropping of Degradation Warnings**: When a scraper is rate-limited or a secondary domain fails to resolve, ignoring the error hides critical context from the user, who simply sees empty attributes without knowing why.

---

## The Core Idea

1. **Contextual Correlation Tracing via MDC**: Every asynchronous job and research pipeline execution attaches deterministic correlation tokens (`jobId`, `entityId`) to the SLF4J Mapped Diagnostic Context. All log statements automatically output these tokens without passing IDs as method parameters.
2. **Mandatory `try-finally` Thread Hygiene**: To prevent thread pollution across pooled threads, every MDC context injection is guaranteed to clean up via `MDC.remove(...)` in a `finally` block.
3. **Structured Pipeline Milestone Logging**: The orchestrator emits standardized milestone logs (`[Pipeline: NORMALIZED]`, `[Pipeline: DISCOVERY]`, `[Pipeline: SOURCE_FILTERED]`, `[Pipeline: ADAPTIVE_STOP]`, `[Pipeline: COMPLETED]`) for real-time observability.
4. **First-Class In-Flight Diagnostics**: Non-fatal operational anomalies (e.g. blocked primary sources, skipped domains, persistence retries) are recorded in a thread-safe `ResearchDiagnostics` accumulator and returned directly in the response payload.
5. **RFC 7807 `ProblemDetail` Error Standardization**: All controllers handle exceptions through `@RestControllerAdvice`, returning RFC 7807 compliant JSON error structures.

---

## How This Project Uses It

```
apps/backend/research-service/
├── research/
│   ├── ResearchOrchestrator.java           # MDC entityId injection & pipeline milestones
│   ├── job/InMemoryResearchJobService.java # MDC jobId injection & async lifecycle logging
│   └── pipeline/
│       ├── ResearchDiagnostics.java        # Thread-safe warning accumulator
│       └── ResearchExecutionTimer.java     # High-precision nanosecond timing
└── common/exception/
    └── GlobalExceptionHandler.java         # RFC 7807 ProblemDetail error mapping
```

### 1. MDC Correlation Injection & Cleanup in [`ResearchOrchestrator.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/research/ResearchOrchestrator.java#L55-L80)

Whenever research is executed for a target, the orchestrator sets `entityId` in the thread's MDC context and guarantees its removal:

```java
public ResearchResponse execute(ResearchRequest request) {
    ResearchExecutionTimer timer = ResearchExecutionTimer.start();
    ResearchTarget target = normalizer.normalize(request);
    ResearchDiagnostics diagnostics = new ResearchDiagnostics();

    // 1. Attach deterministic entityId to thread-local MDC
    MDC.put("entityId", target.entityId());
    try {
        log.info("[Pipeline: NORMALIZED] EntityId='{}', CanonicalUrl='{}', DisplayName='{}', Type='{}'",
                target.entityId(), target.canonicalUrl(), target.displayName(), target.type());

        // ... execute discovery, filtering, extraction, and synthesis ...

        log.info("[Pipeline: COMPLETED] Research finished for entityId: '{}' in {}ms (sources: {}, attributes: {}, warnings: {})",
                target.entityId(), timer.elapsedMillis(), sources.size(), attributes.size(), diagnostics.warningCount());

        return responseFactory.createSuccessResponse(target, sources, attributes, diagnostics, timer.elapsedMillis());
    } finally {
        // 2. Mandatory cleanup: prevents thread pollution in ThreadPoolExecutor
        MDC.remove("entityId");
    }
}
```

The same pattern is used in [`InMemoryResearchJobService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/research/job/InMemoryResearchJobService.java#L52-L70) for background `jobId` tracking:

```java
private void runJob(String jobId, ResearchRequest request) {
    MDC.put("jobId", jobId);
    try {
        log.info("Starting research job execution for jobId: '{}'", jobId);
        // ... execute background job ...
    } catch (Exception ex) {
        log.error("Research job '{}' failed: {}", jobId, ex.getMessage(), ex);
    } finally {
        MDC.remove("jobId"); // Always clean up pooled thread
    }
}
```

### 2. In-Flight Operational Warnings in [`ResearchDiagnostics.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/research/pipeline/ResearchDiagnostics.java)

When a web source cannot be reached (e.g. HTTP 999 anti-bot response on LinkedIn), the scraper logs the event and records an operational warning without aborting the research pipeline:

```java
public class ResearchDiagnostics {
    private final List<String> warnings = new ArrayList<>();

    public synchronized void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            warnings.add(warning.trim());
        }
    }

    public void recordPrimaryInaccessible(String domain, String statusDetail) {
        addWarning("Primary source (" + (domain != null ? domain : "unknown")
                + ") could not be directly fetched (" + (statusDetail != null ? statusDetail : "Inaccessible")
                + "); continuing research using corroborating public sources.");
    }
}
```

In [`DefaultSourceEvidenceService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/extraction/DefaultSourceEvidenceService.java#L98-L105):

```java
if (fetched == null || !fetched.success()) {
    log.info("Primary target source ({}) could not be directly fetched (HTTP {}); continuing with corroborating sources.",
            domain, fetched != null ? fetched.statusCode() : "timeout");
    diagnostics.recordPrimaryInaccessible(domain, "HTTP " + (fetched != null ? fetched.statusCode() : 0));
    // Fall back to search engine snippet text
}
```

The user receives the final response with a non-empty `warnings` array, explaining why direct profile scraping degraded to snippet corroboration.

### 3. RFC 7807 Standardized Errors in [`GlobalExceptionHandler.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/research-service/src/main/java/com/subdual/research_service/common/exception/GlobalExceptionHandler.java)

When unexpected failures or validation violations occur, `GlobalExceptionHandler` converts them into standard Spring 6 / Boot 3+ `ProblemDetail` structures:

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRuleException(BusinessRuleException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Unprocessable Entity");
        problem.setType(URI.create("https://subdual.com/errors/business-rule"));
        return problem;
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalServiceException(ExternalServiceException ex) {
        log.error("External downstream service failure: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Bad Gateway");
        problem.setType(URI.create("https://subdual.com/errors/bad-gateway"));
        return problem;
    }
}
```

A downstream error produces a clean, machine-parsable JSON payload:

```json
{
  "type": "https://subdual.com/errors/bad-gateway",
  "title": "Bad Gateway",
  "status": 502,
  "detail": "ai-intelligent-service connection timed out after 5000ms",
  "instance": "/api/v1/research"
}
```

---

## Flow

```
HTTP Request / Async Task
           │
           ▼
[MDC.put("entityId", id)] ───► Injects thread-local correlation token
           │
           ▼
[Pipeline Milestones Emitted]
- [Pipeline: NORMALIZED]       (displays target type and canonical URL)
- [Pipeline: DISCOVERY]        (displays search query and provider)
- [Pipeline: SOURCE_FILTERED]  (displays rejected namesakes & alumni lists)
- [Pipeline: ADAPTIVE_STOP]    (displays early completion condition)
           │
           ▼
Operational Anomaly Detected? (e.g. HTTP 999 anti-bot)
         /   \
       Yes    No
       /       \
      ▼         ▼
[diagnostics.addWarning(...)]  Continue normal processing
(records warning in payload)
      │         │
      └────┬────┘
           │
           ▼
[Pipeline: COMPLETED] ────────► Emits total elapsed duration in ms
           │
           ▼
[MDC.remove("entityId")] ────► FINALLY block guarantees zero thread contamination
           │
           ▼
Return ResearchResponse with:
- attributes
- sources
- diagnostics.warnings()
- executionDurationMs
```

---

## Important Design Decisions

1. **MDC Over Parameter Passing**:
   Passing `jobId` and `entityId` through 15 private helper methods solely for logging bloats method signatures. MDC leverages Java's `ThreadLocal` storage, making context available to any logger in the call stack automatically.
2. **In-Band Diagnostics vs Exception Throwing**:
   When an external URL fails or LinkedIn returns anti-bot HTTP 999, throwing an exception would abort the entire research job. Recording the incident in `ResearchDiagnostics` allows the pipeline to degrade gracefully and corroborate using snippets while informing the user of the exact degradation reason.
3. **High-Precision Timing with `System.nanoTime()`**:
   In `ResearchExecutionTimer`, elapsed time uses `System.nanoTime()` rather than `System.currentTimeMillis()`. Wall-clock time can jump backwards due to NTP synchronization, whereas monotonic nano timers guarantee accurate latency measurements.

---

## Alternatives

| Approach | Why We Did Not Choose It |
| :--- | :--- |
| **Heavy APM Agent (New Relic / Dynatrace)** | Proprietary, closed-source, and heavy for local development and unit tests. SLF4J MDC + structured logging provides native, zero-dependency tracing out of the box. |
| **Bespoke Error Objects (`ApiResponse<T>`)** | Wrapping every response in `{ success: boolean, data: T, error: string }` breaks standard HTTP status code semantics and prevents standard HTTP caching. |
| **Silent Ignored Fallbacks** | Failing silently when a primary source is blocked produces unexplained missing data. In-band warnings make the pipeline self-auditing. |

---

## Common Mistakes

1. **Omitting MDC Cleanup in `finally` Blocks**:
   Writing `MDC.put("jobId", id)` without a corresponding `MDC.remove("jobId")` inside a `finally` block causes thread leakage. Because Tomcat and `ThreadPoolExecutor` reuse threads, future unrelated requests will log with the old job's ID.
2. **Logging Sensitive Data (PII / Secrets)**:
   Logging API keys (`TAVILY_API_KEY`, `GEMINI_API_KEY`) or raw passwords during network fetch debugging introduces critical security vulnerabilities. Always mask keys in logs.
3. **Leaking Stack Traces to Client Browsers**:
   Returning unhandled Java exception stack traces (`NullPointerException at com.subdual...`) over HTTP reveals internal package structures and dependency versions to potential attackers. Always map exceptions through `GlobalExceptionHandler`.

---

## Production Considerations

* **Centralized Log Aggregation**: In production Kubernetes, container stdout logs are scraped by FluentBit or Promtail and shipped to Grafana Loki or Elasticsearch. MDC keys (`entityId`, `jobId`) are indexed as structured labels, enabling instant log filtering across millions of lines:
  ```logql
  {app="research-service"} |= "entityId=563fe0acc0fc3951"
  ```
* **Micrometer & Prometheus Metrics**: Pairing structured logging with Micrometer timers (`Timer.sample()`) allows Prometheus to scrape latency percentiles (p50, p95, p99) and alert when external scraping latency spikes.
* **W3C TraceContext Propagation**: When graduating to a service mesh, passing W3C `traceparent` headers across HTTP clients links traces across `dataset-service`, `research-service`, and `ai-intelligent-service` in OpenTelemetry and Jaeger.

---

## What I Should Learn From This

1. **Always use MDC for asynchronous request and entity correlation, and ALWAYS clean up in a `finally` block.**
2. **Adopt RFC 7807 `ProblemDetail` for machine-readable, standardized API error responses.**
3. **Distinguish fatal errors (exceptions) from operational degradation (diagnostics warnings).**
4. **Log structured milestone events (`[Pipeline: STAGE]`) to make complex asynchronous pipelines easy to monitor and debug.**

---

**Previous:** [Concept 13: Modular Evidence Extraction, Domain Extractor Decomposition & Entity Resolution](13-modular-evidence-extraction-and-entity-resolution.md) | **Next:** [Concept 15: Dataset Ingestion & Raw Data Boundaries](15-dataset-ingestion-and-raw-data-boundaries.md)
