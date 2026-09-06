# V2 API Evolution Strategy

> [!WARNING]
> **PROPOSED SPECIFICATION / NOT IMPLEMENTED YET**  
> Baseline: `v1.0.0` frozen release.

---

## 1. Core API Strategy & Philosophy

1. **Evolution Over Duplication**: We explicitly reject creating parallel `/api/v2/*` endpoints for every existing API. V2 maintains the existing `/api/v1/` prefix and evolves endpoints through **additive, non-breaking schema expansions**.
2. **Resource-Oriented Modeling**: Shift operational verbs toward RESTful resources where appropriate (`jobs`, `tasks`, `profiles`, `entities`, `evidence`).
3. **Structured Problem Details**: Standardize error payloads using RFC 7807 `application/problem+json`.

---

## 2. API Classification Matrix

```
Existing APIs
   ├── 🟢 Stable (Keep Unchanged)
   ├── 🟡 Extension (Additive Non-Breaking Fields)
   ├── 🔴 Breaking Changes (Explicitly Avoided / Confined)
   └── 🔵 New Endpoints (For Genuine V2 Capabilities)
```

---

## 3. Detailed Endpoint Evolution

### A. Stable APIs (Keep Unchanged)
These endpoints are proven, adhere to clean contracts, and require **zero modifications** in V2:

| Service | Endpoint | HTTP Method | Reason to Keep As-Is |
| :--- | :--- | :--- | :--- |
| **All** | `/actuator/health` | `GET` | Standard Spring Boot readiness/liveness probe used by Docker and load balancers. |
| **All** | `/actuator/info` | `GET` | Standard application build metadata. |
| **`ai-service`** | `/api/v1/ai/clean` | `POST` | Deterministically cleans messy input seeds (names, roles, URLs) with high accuracy. |
| **`dataset-service`** | `/api/v1/entities/{id}` | `GET` | Retrieves full entity snapshot and attribute history by canonical UUID. |

---

### B. APIs Requiring Extension (Additive, Backward-Compatible)
These endpoints preserve all existing V1 request and response fields, adding optional parameters for advanced V2 features:

#### 1. `POST /api/v1/enrichment/jobs` (`dataset-service`)
* **V1 Purpose**: Submits a batch enrichment job across multiple rows.
* **V2 Extension**:
  * **Additive Request Fields**:
    * `profileId` *(UUID, optional)*: References a pre-saved `EnrichmentProfile`.
    * `concurrencyLimit` *(Integer, optional, default: 4)*: Caps concurrent worker threads for this specific job.
    * `webhookUrl` *(String, optional)*: HTTP URL to receive a POST notification upon job completion.
  * **Additive Response Fields**:
    * `state`: Detailed state machine (`PENDING`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`).
    * `estimatedDurationSeconds` *(Long)*.
* **Why**: Allows users to attach saved profiles and configure execution parameters without breaking V1 clients that pass raw `rows` and `userRequirement`.

#### 2. `GET /api/v1/enrichment/jobs/{jobId}` (`dataset-service`)
* **V1 Purpose**: Retrieves overall job status and row results.
* **V2 Extension**:
  * **Additive Query Parameters**:
    * `page` *(Integer, default: 0)* and `size` *(Integer, default: 50)*: Prevents returning massive 5,000-row payloads in a single response.
    * `statusFilter` *(String, optional)*: Filter by `COMPLETED`, `PARTIAL`, or `FAILED`.
  * **Backward Compatibility**: If `page` and `size` are omitted, returns top 100 rows with a `nextPageToken`.

#### 3. `POST /api/v1/research` (`research-service`)
* **V1 Purpose**: Synchronous end-to-end research for a single entity.
* **V2 Extension**:
  * **Additive Request Fields**:
    * `maxSources` *(Integer, default: 5)*: Dynamic discovery depth.
    * `freshnessDays` *(Integer, optional)*: Prefers documents published within N days.
  * **Additive Response Fields**:
    * `provenance`: Detailed metadata per source (HTTP response status, fetch latency, domain authority score).
* **Why**: Enables finer client control over research breadth and temporal freshness.

#### 4. `GET /api/v1/entities` (`dataset-service`)
* **V1 Purpose**: Lists catalog of saved entities from MySQL with pagination.
* **V2 Extension**:
  * **Additive Query Parameters**:
    * `entityType` *(String, optional)*: Filter by `PERSON`, `ORGANIZATION`, `REPOSITORY`, etc.
    * `searchTerm` *(String, optional)*: Substring search across display names and canonical URLs.

---

### C. APIs Requiring Breaking Changes (Evaluated & Rejected)
* **Decision**: **Zero breaking changes will be introduced in V2.**
* **Evaluation**: We considered changing `rowResults` in `/api/v1/enrichment/jobs/{jobId}` from a JSON Array to a paginated Object. Instead of breaking the contract, the endpoint maintains the array in a `results` field while adding a `pagination` envelope metadata object. This guarantees that all V1 client parsers continue to function without error.

---

### D. New APIs (For Genuine V2 Capabilities)
These endpoints introduce specific, non-redundant capabilities required for enterprise batch execution:

#### 1. Job Control Plane (`dataset-service`)
* `POST /api/v1/enrichment/jobs/{jobId}/pause`: Pauses active execution of pending rows in a batch job. Running rows finish cleanly.
* `POST /api/v1/enrichment/jobs/{jobId}/resume`: Resumes execution of a paused or interrupted job from the last unfinished row task.
* `POST /api/v1/enrichment/jobs/{jobId}/cancel`: Gracefully halts remaining tasks, marking unstarted rows as `CANCELLED`.

#### 2. Reusable Enrichment Profiles (`dataset-service` / `ai-service`)
* `GET /api/v1/profiles`: Lists pre-configured and user-defined enrichment schemas (e.g. *Executive Due Diligence*, *B2B Tech Stack Profiler*).
* `POST /api/v1/profiles`: Saves a custom enrichment schema containing field names, descriptions, data types, and confidence thresholds.

#### 3. Granular Evidence Inspection (`research-service`)
* `GET /api/v1/research/evidence/{evidenceId}`: Retrieves full provenance for a specific piece of evidence, including raw HTML snippet, HTTP header details, and character offsets of the verbatim quote.

---

## 4. API Error Handling Standard (RFC 7807)

All microservices will emit standardized `application/problem+json` errors:

```json
{
  "type": "https://api.enrichment.platform/errors/provider-timeout",
  "title": "Search Provider Timeout",
  "status": 504,
  "detail": "Search provider 'Tavily' failed to respond within 4000ms budget.",
  "instance": "/api/v1/research",
  "correlationId": "corr-9f82-a4b1",
  "timestamp": "2026-09-06T12:00:00Z"
}
```
