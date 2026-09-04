# Technical Concepts 21–30 — Spring Implementation & Reliability

> Status: Draft  
> Version: 0.1  
> Last Updated: 2026-09-04

## Purpose

This document provides a concise technical reference on the Spring implementation stack, HTTP client resilience, and execution mechanics required for the Data Enrichment & Research Engine. It aligns strictly with our current architectural decision: implementing a single, reliable Spring Boot application with a minimal synchronous REST API before introducing distributed or asynchronous complexity.

---

### 21. Spring MVC / REST Controller Boundary

**What it is**  
Spring MVC is the annotation-driven HTTP web framework built on the standard Servlet API. A `@RestController` marks a class where every method returns domain objects serialized directly into HTTP response bodies (via Jackson) rather than rendering views.

**Why this project cares**  
The controller layer defines our HTTP boundary (`POST /api/v1/research`). It must remain lean: validating input payloads, delegating execution to the enrichment service, and translating domain outcomes into proper HTTP status codes.

**Relevant to**  
`HTTP endpoint routing → Request deserialization → Response serialization`

**What I need to understand**  
* Controllers must contain **zero business logic**: orchestration, research fetching, and AI interaction belong exclusively in service components.
* Use `@PostMapping("/api/v1/research")` and `@RequestBody` to bind incoming JSON to typed request DTOs.
* Return `ResponseEntity<T>` to set explicit HTTP status codes (`200 OK`, `400 Bad Request`).

**Authoritative reference**  
* [Spring Framework: Web on Servlet Stack (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc.html)

---

### 22. Request Validation in Spring

**What it is**  
Declarative validation using Jakarta Bean Validation (`jakarta.validation.*`) annotations on DTOs, triggered by `@Valid` in Spring MVC controller method signatures.

**Why this project cares**  
Preventing malformed or malicious inputs (blank strings, non-URL protocols, excessive payload lengths) from entering our research pipeline saves expensive network I/O and LLM token costs.

**Relevant to**  
`Request boundary → Schema validation → Early rejection`

**What I need to understand**  
* Annotate request DTOs with `@NotBlank` for required string fields and `@Pattern` or custom validators for well-formed URLs.
* When `@Valid` detects constraints violations, Spring MVC automatically throws `MethodArgumentNotValidException`.
* Validation errors must be caught by a global `@ExceptionHandler` and transformed into standard problem detail responses.

**Authoritative reference**  
* [Jakarta EE: Jakarta Bean Validation Specification](https://jakarta.ee/specifications/bean-validation/)
* [Spring Framework: Validation, Data Binding, and Type Conversion](https://docs.spring.io/spring-framework/reference/core/validation.html)

---

### 23. RFC 9457 Problem Details

**What it is**  
RFC 9457 defines a standardized "problem detail" JSON format (`application/problem+json`) to carry machine-readable error details in HTTP APIs. It obsoletes the earlier RFC 7807 specification.

**Why this project cares**  
Consistent error reporting across client errors (400 validation failures, 404 missing resources) and server errors (500 internal failures, upstream timeouts) enables automated API clients to parse and respond to errors reliably.

**Relevant to**  
`Error handling → Global @ExceptionHandler → Standardized error response`

**What I need to understand**  
* Spring 6 / Spring Boot 3 natively supports RFC 9457 via the `org.springframework.http.ProblemDetail` class.
* Enable default Problem Details support via `spring.mvc.problemdetails.enabled=true` or customize via `@ControllerAdvice` extending `ResponseEntityExceptionHandler`.
* Common fields include: `type` (URI reference), `title` (summary), `status` (HTTP status), `detail` (explanation), and `instance` (request path).

**Authoritative reference**  
* [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) (obsoleted RFC 7807)
* [Spring Framework: Error Responses (ProblemDetail)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)

---

### 24. Spring HTTP Clients (RestClient vs WebClient)

**What it is**  
`RestClient` is the modern, synchronous, fluent HTTP client introduced in Spring Framework 6.1 (Spring Boot 3.2+). `WebClient` is the reactive, non-blocking HTTP client provided by Spring WebFlux.

**Why this project cares**  
Our research engine needs to execute synchronous HTTP GET requests to external websites and search APIs. `RestClient` offers a fluent, modern API without requiring the complex reactive runtime dependencies of WebFlux.

**Relevant to**  
`External web research → HTTP GET execution → Raw HTML fetching`

**What I need to understand**  
* Prefer `RestClient` for our current architecture: it is synchronous, blocking, easy to debug, and pairs well with Java 21 virtual threads.
* Configure explicit connection and read timeouts on the underlying `ClientHttpRequestFactory` (e.g., `JdkClientHttpRequestFactory` or `HttpComponentsClientHttpRequestFactory`).
* Do not introduce Spring WebFlux or `WebClient` merely for HTTP client capability unless a reactive architecture is deliberately chosen.

**Authoritative reference**  
* [Spring Framework: RestClient Documentation](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)

---

### 25. Retry vs Timeout

**What it is**  
A **timeout** defines the maximum allowable duration for a single network operation before terminating it. A **retry** is the deliberate re-execution of a failed operation under the assumption that the failure was transient.

**Why this project cares**  
External websites often stall or return temporary network blips. Timeouts prevent our engine from hanging indefinitely; retries recover transient packet drops. Confusing the two leads to cascading delays.

**Relevant to**  
`Network resilience → Fault tolerance → Latency containment`

**What I need to understand**  
* **Timeouts must always be bounded and strict**: Connection timeout (2s), read timeout (5s).
* Retrying without a timeout compounds latency: 3 retries with infinite timeout will hang forever on an unresponsive server.
* Only retry transient network errors (e.g., connection reset, 503 Service Unavailable); never retry permanent failures (e.g., 404 Not Found, 401 Unauthorized, 400 Bad Request).

**Authoritative reference**  
* [Spring Framework: Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
* [Spring Retry: RetryTemplate and Backoff Policies](https://github.com/spring-projects/spring-retry)

---

### 26. Idempotency and Safe Retries

**What it is**  
An HTTP method is **safe** if it does not alter server state (`GET`, `HEAD`). An HTTP method is **idempotent** if the side effects of multiple identical requests are the same as a single request (`GET`, `HEAD`, `PUT`, `DELETE`). `POST` is neither safe nor idempotent by default.

**Why this project cares**  
External research operations issue `GET` requests, which are strictly safe and idempotent; they can be safely retried. Conversely, internal batch submission APIs using `POST` must generate or accept deterministic idempotency keys (e.g., entity SHA-256) to prevent duplicate processing.

**Relevant to**  
`Outbound research requests → Inbound API design → Duplicate prevention`

**What I need to understand**  
* Outbound research `GET` requests can be retried safely because fetching a public web page is non-mutating.
* Inbound `POST /api/v1/research` uses the seed URL's canonical SHA-256 hash as the canonical entity ID, ensuring duplicate requests resolve to the same logical entity.
* Never retry non-idempotent operations without explicit deduplication safeguards.

**Authoritative reference**  
* [RFC 9110: HTTP Semantics — Section 9.2.1 (Safe Methods) & Section 9.2.2 (Idempotent Methods)](https://www.rfc-editor.org/rfc/rfc9110)

---

### 27. Rate Limiting and Backoff Concept

**What it is**  
Rate limiting restricts the frequency of requests issued to a server within a given time window. Exponential backoff increases the delay between successive retries exponentially (e.g., 1s, 2s, 4s), combined with "jitter" (randomized offset) to prevent the "thundering herd" problem.

**Why this project cares**  
Public search APIs and web servers enforce strict queries-per-second (QPS) limits. Overwhelming external domains causes IP blacklisting (`429 Too Many Requests`), CAPTCHAs, or permanent connection bans.

**Relevant to**  
`Polite HTTP fetching → Upstream quota preservation → Backoff execution`

**What I need to understand**  
* When receiving `429 Too Many Requests`, inspect the `Retry-After` header to honor the server's requested pause.
* Implement polite client-side pacing: enforce a minimum delay (e.g., 500ms–1000ms) between consecutive requests to the same external domain.
* Add randomized jitter to backoff intervals to avoid synchronized request spikes from concurrent worker threads.

**Authoritative reference**  
* [RFC 6585: Additional HTTP Status Codes (Section 4: 429 Too Many Requests)](https://www.rfc-editor.org/rfc/rfc6585)
* [RFC 9110: HTTP Semantics — Section 10.2.7 (Retry-After Header)](https://www.rfc-editor.org/rfc/rfc9110)

---

### 28. Observability: Logs, Metrics, Traces

**What it is**  
Observability comprises three complementary telemetry signals:
1. **Logs**: Structured, timestamped event records detailing discrete actions.
2. **Metrics**: Aggregated numerical measurements over time (request rates, latency histograms, error counters).
3. **Traces**: Distributed request execution paths across service boundaries.

**Why this project cares**  
Diagnosing why an entity failed to enrich requires granular observability: Did DNS fail? Did the URL return 403? Did the LLM run out of tokens? Did the output JSON fail schema validation?

**Relevant to**  
`Debugging → Latency profiling → Operational health`

**What I need to understand**  
* Use structured SLF4J logging with consistent contextual keys (`entityId`, `sourceUrl`, `httpStatus`).
* Spring Boot Actuator and Micrometer provide built-in HTTP server metrics and JVM telemetry out of the box without extra infrastructure.
* Do not log sensitive credentials, API keys, or raw full-page HTML payloads at INFO level.

**Authoritative reference**  
* [Spring Boot Reference: Observability with Micrometer](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.observability)
* [OpenTelemetry Specification: Telemetry Signals](https://opentelemetry.io/docs/specs/otel/)

---

### 29. Testing External Integrations

**What it is**  
Techniques to verify code that interacts with third-party HTTP services and AI APIs without making live, non-deterministic network calls during automated test suites.

**Why this project cares**  
Automated builds (CI/CD) must run offline, reliably, fast, and without consuming external API credits or triggering rate limits.

**Relevant to**  
`Unit testing → Integration testing → CI/CD stability`

**What I need to understand**  
* **MockRestServiceServer**: Spring's built-in mock server for verifying outbound `RestClient` requests and serving recorded mock HTTP responses.
* **WireMock**: A standalone HTTP mock server for testing complex redirect chains, timeouts, and rate-limit responses.
* Test Spring AI interactions by injecting mock or stub `ChatClient` responses rather than invoking real LLM endpoints in unit tests.

**Authoritative reference**  
* [Spring Framework: Testing Client Applications (MockRestServiceServer)](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html)
* [Spring Boot: Testing Spring Boot Applications](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)

---

### 30. Synchronous vs Asynchronous Execution

**What it is**  
Synchronous execution blocks the calling thread until the entire research and extraction cycle completes, returning the result directly in the HTTP response. Asynchronous execution immediately accepts the request, returns a job handle (`202 Accepted`), and processes research in the background for later retrieval.

**Why this project cares**  
Our current API (`POST /api/v1/research`) is **intentionally synchronous**. Understanding why this design was chosen and when it should evolve prevents premature architectural over-engineering.

**Relevant to**  
`System architecture → API design → Scalability roadmap`

**What I need to understand**  
* **CURRENT**: Single-entity synchronous execution on the HTTP request thread. It provides immediate developer feedback, straightforward debugging, and requires zero worker threads, job stores, or polling endpoints.
* **PLANNED**: Asynchronous execution (`GET /api/v1/research/{id}`) will be introduced in Milestone 2 / Phase 2 when batch processing (e.g., hundreds of records) or multi-minute research jobs exceed standard HTTP gateway timeout thresholds (e.g., 30–60s).
* **NOT YET REQUIRED**: Message brokers (Kafka), distributed job queues, or reactive streaming runtimes.

**Authoritative reference**  
* [Project API Design: Evaluation of Asynchronous Tracking](../setup/api-design.md)
* [Java Concurrency & Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
