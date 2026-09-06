# System Architecture

The **Data Enrichment AI Intelligence Platform** is a distributed, production-grade enrichment engine that transforms sparse, noisy, or unstructured entity inputs into verified, structured, requirement-aware profiles backed by verbatim evidence quotes and multi-source confidence tiers.

---

## 1. High-Level Topology & Ingress Architecture

```mermaid
flowchart TB
    subgraph External ["External Clients & Browsers"]
        Browser["User Browser / Client Application"]
    end

    subgraph HostPorts ["Exposed Host Ports (Public Ingress)"]
        Port3000["Port 3000: Web Dashboard"]
        Port9738["Port 9738: API Gateway Ingress"]
    end

    subgraph InternalNet ["Isolated Internal Network (enrichment-network)"]
        subgraph Infra ["Platform Infrastructure Layer"]
            ConfigServer["config-server (:9736)<br/>- Spring Cloud Config Server (native)<br/>- Centralized YAML in /config<br/>- Actuator Health Probes"]
            DiscoveryServer["discovery-server (:9737)<br/>- Spring Cloud Netflix Eureka<br/>- Dynamic Heartbeats & Registry<br/>- Self-Preservation Tuned"]
            ApiGateway["api-gateway (:9738)<br/>- Spring Cloud Gateway (WebMvc)<br/>- Unified Reverse Proxy & Routing<br/>- API Key Auth (X-API-Key)<br/>- CORS & Strict Security Headers<br/>- Client Ingress Normalization"]
        end

        subgraph Backend ["Microservices Layer (Spring Boot / Java 25)"]
            AuthService["auth-service (:9739)<br/>- User Registration & Login<br/>- BCrypt Password Hashing<br/>- HMAC-SHA256 JWT Issuance<br/>- Profile & Session Management<br/>- Flyway users Table"]
            DatasetService["dataset-service (:9743)<br/>- Batch Enrichment Jobs<br/>- Bounded Concurrency (3 Workers)<br/>- Real-Time SSE Stream (:events)<br/>- Row State & Error Isolation<br/>- MySQL Relational Persistence<br/>- User-Scoped Data Ownership"]
            ResearchService["research-service (:9741)<br/>- Entity Identity & Normalization<br/>- Requirement-Aware Search Queries<br/>- Multi-Source Web Discovery<br/>- Polite Fetching & Boilerplate Cleaning<br/>- Verbatim Evidence Extraction<br/>- Multi-Source Corroboration"]
            AIService["ai-intelligent-service (:9742)<br/>- Spring AI (Google GenAI Gemini Client)<br/>- Requirement Interpretation<br/>- Input Data Cleansing<br/>- Fact Extraction & Grounding<br/>- Zero-Hallucination Guardrails<br/>- Deterministic Fallback Engine"]
        end

        subgraph Storage ["Persistence Layer (Internal Port 3306)"]
            MySQL[("MySQL 8.0+<br/>- users<br/>- entities (user_id)<br/>- entity_sources<br/>- entity_attributes<br/>- flyway_schema_history")]
        end
    end

    subgraph CloudAPIs ["External Cloud Providers"]
        SearchAPI["Web Search Engine<br/>(Tavily / Mock Provider)"]
        WebPages["Discovered Web Pages / URLs"]
        LLMProvider["LLM API<br/>(Google Gemini / Vertex)"]
    end

    Browser -->|Port 3000| Port3000
    Browser -->|Port 9738 (REST / SSE)| Port9738
    Port9738 --> ApiGateway

    ApiGateway -->|Reverse Proxy /api/v1/auth/**| AuthService
    ApiGateway -->|Reverse Proxy /api/v1/enrichment/**| DatasetService
    ApiGateway -->|Reverse Proxy /api/v1/entities/**| DatasetService
    ApiGateway -->|Reverse Proxy /api/v1/research/**| ResearchService
    ApiGateway -->|Reverse Proxy /api/v1/sources/**| ResearchService
    ApiGateway -->|Reverse Proxy /api/v1/ai/**| AIService

    Backend -.->|Optional Config Fetch| ConfigServer
    Backend -.->|Heartbeat & Discovery| DiscoveryServer
    ApiGateway -.->|Heartbeat & Discovery| DiscoveryServer

    AuthService -->|JDBC JPA| MySQL
    DatasetService -->|Internal HTTP Client| ResearchService
    DatasetService -->|Internal HTTP Client| AIService
    DatasetService -->|JDBC JPA| MySQL
    ResearchService -->|Internal HTTP Client| AIService
    ResearchService -->|Internal HTTP Client| DatasetService
    ResearchService -->|HTTPS Outbound| SearchAPI
    ResearchService -->|HTTPS Outbound Fetch| WebPages
    AIService -->|HTTPS Outbound| LLMProvider
```

---

## 2. Port Map & Network Isolation

In production VM deployments, **only two ports are bound to the host interfaces**:
* **Port 3000**: Next.js Web Frontend.
* **Port 9738**: API Gateway (Unified ingress entry point).

All internal business microservices, the configuration server, Eureka registry, and the MySQL database reside exclusively inside the private Docker bridge network (`enrichment-network`), unreachable directly from outside the VM.

| Service | Port | Network Scope | Technology | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **`config-server`** | `9736` | Internal-only | Spring Cloud Config Server | Native file-based centralized configuration store |
| **`discovery-server`** | `9737` | Internal-only | Spring Cloud Netflix Eureka | Dynamic service registry and health awareness |
| **`api-gateway`** | `9738` | **Public Host** | Spring Cloud Gateway (WebMvc) | Unified ingress, reverse proxy, JWT auth, anti-spoofing, CORS |
| **`auth-service`** | `9739` | Internal-only | Spring Boot 4.1.1 / Java 25 | User accounts, BCrypt passwords, HMAC-SHA256 JWT tokens |
| **`research-service`** | `9741` | Internal-only | Spring Boot 4.1.1 / Java 25 | Web research, multi-source scraping, corroboration |
| **`ai-intelligent-service`** | `9742` | Internal-only | Spring Boot 4.1.1 / Java 25 | LLM grounding, entity cleansing, schema interpretation |
| **`dataset-service`** | `9743` | Internal-only | Spring Boot 4.1.1 / Java 25 | Ingestion, async batch worker pools, SSE events, JPA persistence |
| **`mysql`** | `3306` | Internal-only | MySQL 8.0+ | Relational schema persistence and Flyway history |
| **`frontend`** | `3000` | **Public Host** | Next.js 15 / React / Tailwind | Interactive multi-stage dataset enrichment UI |

---

## 3. Platform Infrastructure Layer

### A. Centralized Configuration (`config-server` on Port 9736)
* **Storage Model**: `native` profile loading YAML definitions directly from `/config` volume mount (local repository directory `config/`).
* **Client Integration**: Business services use `spring.config.import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:9736}`.
* **Fault Tolerance**: If the Config Server is temporarily unavailable during local development, services smoothly fallback to their embedded `application.yaml` defaults without crashing.
* **Config Profiles**:
  * `application.yml`: Shared defaults (Eureka client registration, Actuator health probes, Logback MDC pattern).
  * `auth-service.yml`: Datasource connection, Flyway migrations, JWT secret and token expiration.
  * `research-service.yml`: Research timeouts, max sources, scraper buffer thresholds.
  * `ai-intelligent-service.yml`: Gemini model, temperature, deterministic fallback thresholds.
  * `dataset-service.yml`: JPA connection pools, Flyway execution, thread pool concurrency.
  * `api-gateway.yml`: Gateway route rules, CORS allowed origins, security parameters.

### B. Dynamic Service Discovery (`discovery-server` on Port 9737)
* **Engine**: Spring Cloud Netflix Eureka Server configured in standalone mode (`register-with-eureka: false`, `fetch-registry: false`).
* **Client Behavior**: All microservices register dynamically upon startup, announcing their hostnames and ports with periodic heartbeats (lease renewal 10s, expiration 30s).
* **Dual Resolution**: Supports Eureka service IDs (`lb://SERVICE-NAME`) or direct container name resolution via Docker internal DNS.

### C. Unified API Gateway (`api-gateway` on Port 9738)
* **Engine**: Spring Cloud Gateway Server (WebMvc) on Spring Boot 4.1.1 and Java 25.
* **Routing Strategy**:
  * `/api/v1/auth/**` $\rightarrow$ `auth-service` (:9739)
  * `/api/v1/enrichment/**` $\rightarrow$ `dataset-service` (:9743)
  * `/api/v1/entities/**` $\rightarrow$ `dataset-service` (:9743)
  * `/api/v1/research/**` $\rightarrow$ `research-service` (:9741)
  * `/api/v1/sources/**` $\rightarrow$ `research-service` (:9741)
  * `/api/v1/ai/**` $\rightarrow$ `ai-intelligent-service` (:9742)
  * `/actuator/**` $\rightarrow$ Local Gateway Actuator health & info
* **Security & Ingress Protection**:
  * **JWT Bearer Authentication**: `JwtAuthenticationFilter` serves as the primary authentication boundary. Requests to protected routes must provide an `Authorization: Bearer <jwt>` header signed with HMAC-SHA256 (`jwt.secret`). Missing or expired tokens return a RFC 7807 401 Unauthorized problem response.
  * **Public Exceptions**: `/api/v1/auth/register`, `/api/v1/auth/login`, `/actuator/**`, and HTTP `OPTIONS` preflight requests bypass token validation.
  * **Anti-Spoofing Header Normalization**: `HeaderMapRequestWrapper` intercepts every request at the Gateway and automatically strips any incoming client `X-User-Id` or `X-User-Email` headers. Only after cryptographically verifying the signed JWT are validated `X-User-Id` and `X-User-Email` headers injected downstream.
  * **API Key Auth**: When configured, `GATEWAY_API_KEY` provides optional infrastructure-level authorization.
  * **Security Headers**: Injects `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Permissions-Policy: geolocation=(), microphone=(), camera=()`.
  * **Centralized CORS**: Handles preflight across all microservices, allowing configured frontend origins and exposing SSE/correlation headers.

---

## 4. Microservice Responsibilities & Boundaries

### A. `auth-service` (Port 9739)
* **Core Principle**: *"Auth Service owns user identity, credential security, and JWT lifecycle."*
* **Primary Responsibilities**:
  1. **User Registration (`/api/v1/auth/register`)**: Validates email uniqueness and input constraints, hashes passwords via BCrypt (strength 10), creates user record, and issues an initial JWT token.
  2. **User Authentication (`/api/v1/auth/login`)**: Authenticates credentials against stored BCrypt hashes and generates an HMAC-SHA256 Bearer JWT.
  3. **User Profile Retrieval (`/api/v1/auth/me`)**: Returns currently authenticated user details derived from token or forwarded header identity.
  4. **Relational User Persistence**: Manages the `users` table via versioned Flyway migrations.

### B. `research-service` (Port 9741)
* **Core Principle**: *"Research produces evidence."*
* **Primary Responsibilities**:
  1. **Seed Input Identification & Normalization**: Canonicalizes raw URLs, removes tracking parameters (`utm_*`, `ref`), and handles composite identities across person names, titles, and organizations.
  2. **Requirement-Aware Search Query Formulation**: Combines entity identifiers and target field requirements into optimized search queries via `QueryBuilder` and strategy adapters.
  3. **Multi-Source Discovery & Primary Source Ranking**: Queries search providers (Tavily with graceful `MockSearchProvider` fallback) and prioritizes authoritative primary domains.
  4. **Web Content Fetching & Boilerplate Cleaning**: Fetches HTML/JSON/Markdown content, strips noise, navigation links, and ads, extracting core text sections with size and timeout guardrails.
  5. **Evidence Extraction**: Specialized field extractors alongside delegation to `ai-intelligent-service`.
  6. **Multi-Source Corroboration & Conflict Resolution**: Merges overlapping evidence, flags conflicting claims across sources, and computes confidence tiers (`HIGH`, `MEDIUM`, `LOW`).
  7. **User-Scoped Job Isolation**: Asynchronous research jobs bind to the authenticated caller (`userId`), preventing cross-tenant access.

### C. `ai-intelligent-service` (Port 9742)
* **Core Principle**: *"AI produces clean, structured, requirement-aware enrichment without hallucination."*
* **Primary Responsibilities**:
  1. **Requirement Interpretation (`/api/v1/ai/requirement`)**: Parses free-form natural language requirements into structured target fields and priority search keywords.
  2. **Input Cleansing (`/api/v1/ai/clean`)**: Cleans noisy names, strips emojis, parses compound roles and titles, and normalizes URLs.
  3. **Fact Extraction & Grounding (`/api/v1/ai/enrich`)**: Extracts structured factual tuples from raw text. Every extracted attribute **must** be grounded in an exact verbatim quote. If evidence is missing, the value is marked `UNKNOWN`.
  4. **Profile Assessment (`/api/v1/ai/profile/assess`)**: Evaluates multi-dimensional alignment against objective targets.
  5. **Transparent Deterministic Fallback**: When external LLM APIs are unreachable, rate-limited, or return invalid JSON, a deterministic heuristic engine provides continuous operation.

### D. `dataset-service` (Port 9743)
* **Core Principle**: *"Dataset Service owns dataset ingestion, job execution, and relational persistence."*
* **Primary Responsibilities**:
  1. **Batch Enrichment Job Execution (`/api/v1/enrichment/jobs`)**: Orchestrates parallel row processing over a bounded `ThreadPoolTaskExecutor` (default: 3 workers, configurable). Each job is permanently associated with the authenticated `userId`.
  2. **Real-Time Observability & SSE Scoping (`/api/v1/enrichment/jobs/{jobId}/events`)**: Streams discrete row lifecycle events (`STARTED`, `RESEARCH`, `AI_EXTRACTION`, `PERSISTENCE`, `COMPLETED`, `FAILED`) via Server-Sent Events (SSE). Users can only subscribe to their own jobs; cross-tenant access returns 404/403.
  3. **Row-Level Error Isolation**: Network timeouts, missing web pages, or parsing errors on an individual row are isolated—the failing row is marked `FAILED` while remaining rows continue unhindered.
  4. **Job Cancellation Lifecycle (`/api/v1/enrichment/jobs/{jobId}/cancel`)**: Allows operators and owners to cancel queued jobs instantly while in-flight workers complete gracefully. Cross-user cancellations are strictly rejected.
  5. **User-Scoped Relational Entity Persistence (`/api/v1/entities`)**: Persists canonical entity records, discovered sources, and attribute evidence into MySQL, stamping the owner's `user_id`. Queries for entities filter by the caller's `user_id` while preserving unowned entities (`user_id IS NULL`) for backward compatibility.

### E. `frontend` (Port 3000)
* **Core Principle**: *"Frontend guides the user through progressive enrichment and transparently displays grounded evidence."*
* **Primary Responsibilities**:
  1. **Authentication & Session Lifecycle**: Dedicated `/login`, `/register`, and `/account` views with `AuthContext` state management, automatic JWT Bearer header injection, and graceful redirect to login on 401.
  2. **Progressive 5-Stage Workflow**: Upload $\rightarrow$ Preview & Profile $\rightarrow$ Column Mapping $\rightarrow$ Live Processing Dashboard $\rightarrow$ Results & Export.
  3. **Unified API Gateway Communication**: Dispatches all research, AI, and dataset requests to the Gateway (`http://localhost:9738`), passing the JWT Bearer authorization token.
  4. **Live Execution Dashboard**: Real-time worker cards, progress metrics, active stages, activity log, and immediate modal inspection of finished rows.
  5. **Evidence Transparency**: Attribute confidence badges, conflict indicators, and detailed multi-tab evidence inspection modal showing exact quotes and provenance URLs.
  6. **Non-Destructive Export**: Appends new `Enriched_*` attributes, canonical URLs, and confidence metadata while strictly preserving all original spreadsheet columns.

---

## 5. Domain & Relational Data Model

### Domain Concepts
* **User**: Registered platform account (`id`, `name`, `email`, `password_hash`, `enabled`, `created_at`, `updated_at`).
* **Entity**: The logical subject being enriched (Person, Organization, Product, Repository, Website). Identified by a deterministic SHA-256 hash of its composite identity, optionally scoped to a `user_id`.
* **Source**: A web document discovered during research, categorized by `sourceType` (`PRIMARY`, `SOCIAL_PROFILE`, `SEARCH_DISCOVERY`, `ACADEMIC`, `REGISTRY`) and tracked with domain, provider, and relevance score.
* **Evidence Tuple**: An atomic verifiable claim: `(attributeName, value, exactQuote, sourceUrl, confidence)`.
* **Corroborated Attribute**: An attribute consolidated across multiple sources, noting corroborating URLs, confidence tier (`HIGH`, `MEDIUM`, `LOW`), and any detected conflicts.

### Relational Schema (MySQL 8.0+)
Managed via versioned Flyway migrations in `auth-service` and `dataset-service`:

```sql
-- Users Table (auth-service V1)
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email)
);

-- Core Entity Table (dataset-service V1 + V3 user_id)
CREATE TABLE entities (
    entity_id VARCHAR(64) PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    canonical_url VARCHAR(1024) NOT NULL,
    user_id VARCHAR(36) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_entities_type (entity_type),
    INDEX idx_entities_updated (updated_at),
    INDEX idx_entities_user_id (user_id)
);

-- Discovered Sources
CREATE TABLE entity_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    title VARCHAR(512),
    snippet TEXT,
    source_type VARCHAR(32) NOT NULL,
    domain VARCHAR(255),
    provider VARCHAR(64),
    relevance DOUBLE,
    retrieved_at TIMESTAMP,
    CONSTRAINT fk_source_entity FOREIGN KEY (entity_id) REFERENCES entities(entity_id) ON DELETE CASCADE,
    INDEX idx_sources_entity (entity_id)
);

-- Grounded Attributes & Evidence
CREATE TABLE entity_attributes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    attribute_key VARCHAR(128) NOT NULL,
    attribute_value TEXT NOT NULL,
    source_url VARCHAR(1024),
    evidence_snippet TEXT,
    confidence VARCHAR(32) NOT NULL,
    CONSTRAINT fk_attribute_entity FOREIGN KEY (entity_id) REFERENCES entities(entity_id) ON DELETE CASCADE,
    INDEX idx_attributes_entity (entity_id),
    INDEX idx_attributes_key (attribute_key)
);
```

---

## 6. Observability, Tracing & Resilience

* **Actuator Probes**: All backend microservices expose `/actuator/health` (with liveness and readiness state) and `/actuator/info`.
* **Central Logging**: Uniform Logback format outputting timestamp, process PID, thread name, logger category, and correlation identifiers.
* **Graceful Degradation**: 
  * If Config Server is unreachable $\rightarrow$ Client services fall back to local `application.yaml`.
  * If Eureka is unreachable $\rightarrow$ Gateway and internal clients route using direct container DNS (`http://service-name:port`).
  * If External Search API / LLM is unreachable $\rightarrow$ Graceful fallback to heuristic extraction and mock provider without crashing batch jobs.
