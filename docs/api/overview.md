# API Architecture & Gateway Ingress

This document details the ingress architecture, authentication mechanisms, anti-spoofing security protections, distributed tracing, error contract standards, and the architectural segregation between **Public API DTOs** and **Internal Inter-Service DTOs**.

---

## 1. Gateway Ingress Layer (`:8080` / `:9738`)

All external client interactions—including the Next.js Web Frontend, developer SDKs, and third-party integrations—ingress via the **API Gateway**. The Gateway acts as the single unified entry point, enforcing reverse-proxy routing, JWT Bearer token authentication, anti-spoofing header normalization, optional API key checks, strict security headers, and centralized Cross-Origin Resource Sharing (CORS).

```
                      Client (Browser / API Consumer)
                                     │
                 Authorization: Bearer <JWT>  (or X-API-Key)
                                     ▼
                ┌─────────────────────────────────────────┐
                │           API Gateway (:8080)           │
                │  - JWT Signature & Expiry Verification  │
                │  - Strip spoofed X-User-* headers       │
                │  - Inject verified X-User-Id / Email    │
                │  - Inject / Propagate X-Correlation-ID  │
                └────────────────────┬────────────────────┘
                                     │
           ┌─────────────────────────┼─────────────────────────┐
           ▼                         ▼                         ▼
   auth-service:9739        dataset-service:9743      research-service:9741
```

### Ingress Routing Table

| Ingress Path Pattern | Target Microservice | Eureka Service ID | Purpose |
| :--- | :--- | :--- | :--- |
| `/api/v1/auth/**` | `auth-service:9739` | `AUTH-SERVICE` | User registration, authentication, token issuance, profile |
| `/api/v1/research/**` | `research-service:9741` | `RESEARCH-SERVICE` | Synchronous & async entity research pipelines |
| `/api/v1/sources/**` | `research-service:9741` | `RESEARCH-SERVICE` | Discovered source retrieval & verification |
| `/api/v1/ai/**` | `ai-intelligent-service:9742` | `AI-INTELLIGENT-SERVICE` | Requirement parsing, data cleansing, LLM grounding |
| `/api/v2/ai/**` | `ai-intelligent-service:9742` | `AI-INTELLIGENT-SERVICE` | AI requirement planning, objective parsing, profile assessment |
| `/api/v1/enrichment/**` | `dataset-service:9743` | `DATASET-SERVICE` | Batch dataset enrichment jobs & SSE real-time events |
| `/api/v1/entities/**` | `dataset-service:9743` | `DATASET-SERVICE` | Persisted entity records, sources, and attributes |
| `/api/v2/datasets/**` | `dataset-service:9743` | `DATASET-SERVICE` | Multipart dataset upload, delimiter sniffing, schema profiling |
| `/actuator/health` | Local Gateway | N/A | Gateway liveness and readiness health probes |
| `/actuator/info` | Local Gateway | N/A | Gateway build and runtime metadata |

---

## 2. Authentication & Ingress Security

### 2.1 JWT Bearer Token Authentication
* **Header**: `Authorization: Bearer <token>`
* The API Gateway validates tokens signed with HMAC-SHA256 (`jwt.secret`).
* The token payload contains the subject (`userId`), `email`, issuance timestamp, and expiration time.

### 2.2 Anti-Spoofing Architecture
In a microservices mesh, downstream services often rely on HTTP headers (e.g., `X-User-Id`) to enforce multi-tenancy and data isolation. If a malicious client passes `X-User-Id: admin`, an unprotected downstream service could execute an Insecure Direct Object Reference (IDOR) attack.

To prevent this:
1. The Gateway uses a custom `HeaderMapRequestWrapper` on every incoming request.
2. It unconditionally **strips any client-supplied** `X-User-Id`, `X-User-Email`, or `X-User-Roles` headers before inspecting credentials.
3. Upon successful JWT verification, the Gateway decodes the claims and **injects verified downstream headers**:
   * `X-User-Id`: Extracted securely from the verified JWT `sub` claim.
   * `X-User-Email`: Extracted securely from the verified JWT `email` claim.
4. Downstream microservices (`dataset-service`, etc.) can safely trust `X-User-Id` because direct external ingress to internal ports is disallowed in production.

### 2.3 Whitelisted Endpoints
The following endpoints bypass JWT authentication at the Gateway:
* `POST /api/v1/auth/register` (Account creation)
* `POST /api/v1/auth/login` (Token issuance)
* `/actuator/health` and `/actuator/info` (Infrastructure monitoring)
* HTTP `OPTIONS` requests (CORS preflights)

### 2.4 Optional API Key Validation
* **Header**: `X-API-Key: <key>`
* Controlled by the `GATEWAY_API_KEY` configuration property.
* When configured, requests lacking a matching key are rejected with `401 Unauthorized` before reaching route evaluation.

### 2.5 Standard Injected Security Headers
Every HTTP response mediated through the gateway includes:
* `X-Content-Type-Options: nosniff`
* `X-Frame-Options: DENY`
* `Referrer-Policy: strict-origin-when-cross-origin`
* `Permissions-Policy: geolocation=(), microphone=(), camera=()`

---

## 3. Distributed Tracing & Correlation

Every request traversing the platform is tracked via a correlation identifier:
* **Header**: `X-Correlation-ID`
* **Behavior**:
  - If supplied by the client or upstream proxy, the Gateway preserves and propagates it.
  - If omitted, the Gateway generates a UUID v4 and injects it into both downstream request headers and the client HTTP response headers.
  - Downstream services register an MDC (Mapped Diagnostic Context) filter that populates `correlationId` into all SLF4J log patterns:
    ```
    2026-09-10 02:00:00.123 [corrId=f47ac10b-58cc-4372-a567-0e02b2c3d479] INFO  c.s.d.b.BatchCoordinator - Row 0 completed
    ```

---

## 4. Standard Error Contract (RFC 7807)

All microservices adhere to the RFC 7807 **Problem Details for HTTP APIs** specification for client errors (4xx) and server errors (5xx):

```json
{
  "type": "https://api.enrichment.platform/errors/entity-not-found",
  "title": "Entity Not Found",
  "status": 404,
  "detail": "No entity found with ID 'e8a1d7c4-0000-4000-a000-000000000000'",
  "instance": "/api/v1/entities/e8a1d7c4-0000-4000-a000-000000000000",
  "code": "ENTITY_NOT_FOUND",
  "requestId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-09-10T02:00:00.123Z"
}
```

### Standard Status Codes

| HTTP Status | Meaning | Typical Trigger |
| :--- | :--- | :--- |
| `200 OK` | Success | Query fulfilled or synchronous research/cleansing complete |
| `201 Created` | Resource Created | User registered |
| `202 Accepted` | Asynchronous Job Queued | Batch enrichment job or async research job accepted |
| `400 Bad Request` | Validation Failure | Missing required fields, malformed CSV headers |
| `401 Unauthorized` | Authentication Missing/Invalid | Missing/expired JWT, invalid credentials, wrong API key |
| `403 Forbidden` | Access Denied | Tenant boundary violation (accessing another user's job) |
| `404 Not Found` | Resource Missing | Unknown entity ID, unknown job ID |
| `409 Conflict` | State Conflict | Registering an existing email address |
| `422 Unprocessable Entity` | Semantic Failure | Valid JSON schema but logically inconsistent parameters |
| `500 Internal Server Error` | Unhandled Exception | Downstream unhandled crash |
| `502 Bad Gateway` | Upstream Failure | Hard downstream service outage without fallback |
| `503 Service Unavailable` | Overload / Circuit Broken | Rate limiter or circuit breaker active |

---

## 5. Public API DTOs vs. Internal Inter-Service DTOs

A fundamental architectural rule of this platform is the **strict segregation between Public Ingress DTOs and Internal Inter-Service DTOs**.

### 5.1 Why Separate Them?

```
                    External Clients (Web, SDK, CLI)
                                   │
                      PUBLIC API DTOs (Stable)
                    api.dto.request / api.dto.response
                                   │
                                   ▼
                       ┌──────────────────────┐
                       │   dataset-service    │
                       └───────────┬──────────┘
                                   │
                  INTERNAL INTER-SERVICE DTOs (Coupled)
                     integration.ai.dto / integration.persistence.dto
                                   │
                                   ▼
                       ┌──────────────────────┐
                       │  research-service    │
                       └──────────────────────┘
```

1. **Independent API Evolution**: External API contracts change slowly to avoid breaking web clients and external consumers. Internal inter-service communication protocols evolve rapidly to support new algorithmic capabilities, caching strategies, or performance optimizations.
2. **Security & Information Hiding**: Public API DTOs must not leak internal system structures (e.g., raw HTML snapshots, database primary keys, internal crawler worker thread IDs, AI token counts).
3. **Resilience & Fallback Mapping**: When a downstream internal service responds with a degraded or fallback structure (e.g., deterministic heuristic fact extraction when Spring AI times out), the calling service maps this internal structure into a consistent, stable public response model.
4. **Validation Separation**: Public DTOs enforce strict JSR-380 validation (`@NotBlank`, `@Pattern`, `@Size`), while internal DTOs represent domain-verified transfer objects.

### 5.2 Concrete Package Boundaries

#### Public API Contracts (`api/dto/`)
Located in each service under `com.subdual.<service>.api.dto`:
* **`api.dto.request`**: Incoming payload definitions accepted by `@RestController` controllers (e.g., `JobSubmissionRequest`, `SingleEnrichmentRequest`, `LoginRequest`).
* **`api.dto.response`**: Outgoing JSON representations returned to callers (e.g., `JobStatusResponse`, `RowEnrichmentResultResponse`, `UserProfileResponse`).

#### Internal Inter-Service Contracts (`integration/**/dto/`)
Located in caller services under `com.subdual.<service>.integration.<target>.dto`:
* In `dataset-service`:
  * `integration.ai.dto`: Structures sent to/from `ai-intelligent-service` (e.g., `FactExtractionPayload`, `RequirementPlanPayload`).
  * `integration.persistence.dto`: Internal data models mapped before writing to MySQL JPA repositories.
* In `research-service`:
  * `integration.ai.dto`: Structs sent to `ai-intelligent-service` during the research pipeline.
* In caller clients:
  * `integration.client.dto`: Payloads explicitly modeling downstream REST responses, completely decoupled from the receiving service's public controller DTOs.

---

## 6. Real-Time Streaming Protocol (Server-Sent Events)

For long-running batch enrichment, the platform provides a reactive, real-time Server-Sent Events (SSE) stream over HTTP/1.1 or HTTP/2:

* **Endpoint**: `GET /api/v1/enrichment/jobs/{jobId}/events`
* **Media Type**: `text/event-stream`
* **Connection Management**:
  - Emits an initial `init` event upon subscription containing dataset metadata and worker concurrency.
  - Emits granular `execution-event` messages as rows transition through worker pools (`RESEARCH`, `AI_EXTRACTION`, `PERSISTENCE`).
  - Emits a terminal `job-completed` event when the batch completes or terminates.
  - Automatically times out idle connections after 30 minutes.
  - Clients (Next.js `EventSource`) automatically reconnect with last-event tracking.
