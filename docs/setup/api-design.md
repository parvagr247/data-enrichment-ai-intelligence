# API Design

> Status: Draft  
> Version: 0.1  
> Last Updated: 2026-09-04

## Purpose

This document details the minimal HTTP API design for the Data Enrichment & Research Engine. It establishes the smallest useful API surface required to validate the core single-entity enrichment workflow while maintaining clean architectural boundaries for future evolution.

---

## 1. Core API Workflow

The API exposes the fundamental research and enrichment experiment over HTTP:

```text
HTTP Client (cURL / Frontend / Script)
                 │
                 ▼  POST /api/v1/research
         [Input Seed URL]
                 │
                 ▼
       Autonomous Research
                 │
                 ▼
        Evidence Retrieval
                 │
                 ▼
       Spring AI Extraction
                 │
                 ▼
   200 OK: Structured JSON Result + Provenance
```

---

## 2. API Design Principles

1. **RESTful Conventions**: Standard HTTP methods, predictable resource-oriented URIs, and standard status codes.
2. **Domain-Oriented Operations**: APIs represent high-level domain actions (`research`, `enrichment`) rather than generic database CRUD operations.
3. **Strict Validation**: Explicit validation on incoming payloads (e.g., non-blank fields, well-formed HTTP/HTTPS URLs).
4. **Consistent Error Reporting**: Adherence to the RFC 7807 `ProblemDetail` specification for all client (4xx) and server (5xx) errors.
5. **No Premature Endpoints**: Exclude administrative, batch, or hypothetical lifecycle endpoints until a concrete consumer requires them.
6. **No Premature CRUD**: The engine is a research and transformation platform, not an entity database editor.
7. **Pragmatic Versioning**: Simple URI-based versioning (`/api/v1/...`) to provide clear evolutionary boundaries without complex header negotiation.

---

## 3. Minimal Current API Surface

For the initial implementation, the API surface consists of a **single endpoint**.

### 3.1 Single-Entity Research

Executes a synchronous research and enrichment cycle for a single entity seed URL.

* **Endpoint**: `POST /api/v1/research`
* **Content-Type**: `application/json`

#### Request Payload

```json
{
  "url": "https://example.com/profiles/jane-doe",
  "entityType": "PERSON",
  "name": "Jane Doe"
}
```

#### Request Field Specifications

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `url` | String | **Yes** | Valid, accessible HTTP or HTTPS URL acting as the research seed. |
| `entityType` | String | No | Optional classification hint (`PERSON`, `ORGANIZATION`, `PRODUCT`, `REPOSITORY`, `WEBSITE`, `OTHER`). Defaults to `OTHER` if omitted. |
| `name` | String | No | Optional display name hint to guide research disambiguation. |

#### Successful Response (`200 OK`)

```json
{
  "status": "COMPLETED",
  "entityId": "f2d8a4365b987b7a123e456789abcdef0123456789abcdef0123456789abcdef",
  "result": {
    "displayName": "Jane Doe",
    "entityType": "PERSON",
    "canonicalUrl": "https://example.com/profiles/jane-doe",
    "attributes": {
      "currentRole": {
        "value": "Principal Infrastructure Engineer",
        "sourceUrl": "https://example.com/profiles/jane-doe",
        "evidenceSnippet": "Jane Doe is a Principal Infrastructure Engineer specializing in distributed data pipelines.",
        "confidence": "HIGH"
      },
      "organization": {
        "value": "Acme Technologies",
        "sourceUrl": "https://example.com/profiles/jane-doe",
        "evidenceSnippet": "Currently leading core platform teams at Acme Technologies.",
        "confidence": "HIGH"
      },
      "secondaryEducation": {
        "value": "UNKNOWN",
        "sourceUrl": null,
        "evidenceSnippet": null,
        "confidence": "UNKNOWN"
      }
    }
  },
  "sources": [
    {
      "url": "https://example.com/profiles/jane-doe",
      "retrievedAt": "2026-09-04T12:40:00Z",
      "sourceType": "DIRECT_PAGE"
    }
  ],
  "executionTimeMs": 1450
}
```

#### Error Response (`400 Bad Request` / `500 Internal Server Error`)

Follows standard RFC 7807 `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Field 'url' must be a valid, well-formed HTTP/HTTPS URL",
  "instance": "/api/v1/research"
}
```

---

## 4. Evaluation of Asynchronous Tracking: `GET /api/v1/research/{id}`

An asynchronous polling endpoint (`GET /api/v1/research/{id}`) was evaluated:

* **Why it was considered**: Web research and LLM extraction take between 1 and 10 seconds. In production batch environments, long-running operations are typically queued asynchronously.
* **Evaluation for Current State**: In the initial development phase, synchronous execution provides immediate developer feedback, simplifies debugging, and eliminates the need for background worker threads, job state stores, or polling logic.
* **Decision**: 
  * `GET /api/v1/research/{id}` is **NOT YET REQUIRED** for the current single-entity proof-of-concept.
  * It is designated as **PLANNED** for Milestone 2 / Phase 2 when asynchronous batch processing or deep multi-query research workflows require deferred result retrieval.

---

## 5. Microservice Boundaries (Future Architectural Context)

The current implementation runs entirely within a **single Spring Boot application**. 

Hypothetical microservice APIs are deliberately omitted to avoid premature architectural overhead. If processing volume or operational scale warrants decomposition in the future, the system may split along these three functional boundaries:

1. **Research & Enrichment Service**: Autonomous web fetching, search queries, and evidence compilation.
2. **AI Intelligence Service**: LLM prompt execution, structured extraction, and qualitative scoring.
3. **Dataset & Export Service**: Tabular file ingestion, persistence, and downstream CSV/JSON export.

> **Note**: These boundaries are documented for future reference only. Their service-to-service communication contracts and APIs will be specified only if decomposition is undertaken.

---

## 6. Spring Ecosystem Alignment

The API implementation relies directly on idiomatic Spring Boot conventions:

* **Spring Web**: Standard `@RestController`, request mapping, and RFC 7807 `ProblemDetail` error handling.
* **Spring AI**: ChatClient integration and tool-calling execution triggered directly within service layers.
* **Spring Validation**: Declarative `@Valid`, `@NotBlank`, and `@URL` validation on incoming request DTOs.

### Deferred Dependencies (Adopted ONLY when required):
* **Spring Data / PostgreSQL**: Deferred until persistent storage is needed.
* **Spring Kafka**: Deferred until asynchronous queueing is required for batch scaling.
* **Spring Cloud (Gateway / Eureka)**: Deferred until microservice decomposition is justified.

---

## 7. Current vs. Planned vs. Not Yet Required

| Architectural Area | CURRENT (Implementing Now) | PLANNED (Future Milestones) | NOT YET REQUIRED (Deliberately Excluded) |
| :--- | :--- | :--- | :--- |
| **API Surface** | Synchronous `POST /api/v1/research` | Async `GET /api/v1/research/{id}`, `POST /api/v1/research/batch` | 30+ CRUD endpoints, GraphQL, gRPC, WebSocket streaming |
| **Application Packaging** | Single Spring Boot monolith | Possible 2–3 service boundaries | Microservice mesh, distributed API Gateway |
| **Persistence & State** | In-memory execution, zero DB | Relational storage for entity and job history | Distributed caching clusters, distributed transactions |
| **Job Execution** | Direct synchronous HTTP thread | In-process queue or Redis job list | Apache Kafka broker, event sourcing, complex workflow engines |
