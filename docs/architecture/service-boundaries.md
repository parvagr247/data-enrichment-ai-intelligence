# Microservice Boundaries & Responsibilities

In distributed systems, strict boundary enforcement prevents architectural erosion, coupling, and circular dependencies. The platform divides responsibilities across dedicated bounded contexts, ensuring each service has a single, well-defined authority.

---

## 1. Service Responsibility Matrix

| Service | Primary Authority / Bounded Context | What It DOES | What It DOES NOT Do |
| :--- | :--- | :--- | :--- |
| **`dataset-service`** | Dataset Ingestion, Job Execution & Relational Persistence | Parses tabular files, coordinates bounded parallel worker pools, tracks row execution states, publishes SSE events, and persists relational entities in MySQL. | Does NOT scrape the web, execute search queries, invoke LLM APIs directly, or manage user passwords. |
| **`research-service`** | Web Research, Scraping & Evidence Extraction | Discovers web pages via search providers, ranks sources, scrapes HTML, strips boilerplate, extracts factual tuples, and corroborates claims across sources. | Does NOT persist batch dataset jobs, generate LLM prompts directly, or serve user authentication. |
| **`ai-intelligent-service`** | LLM Prompting, Schema Interpretation & Structured Intelligence | Interprets natural language requirements into structured fields, cleans input names/titles, extracts grounded facts from text, and computes objective fit scores. | Does NOT perform web crawling, query search engines, persist data to MySQL, or coordinate multi-row batch threads. |
| **`auth-service`** | User Accounts & Security Credentials | Registers users, hashes passwords with BCrypt, issues HMAC-SHA256 JWT tokens, and manages the `users` relational table. | Does NOT handle research, datasets, or external AI calls. |
| **`api-gateway`** | Unified Ingress, Authentication & Anti-Spoofing | Acts as reverse proxy, validates JWT Bearer tokens, strips client-spoofed user identity headers, applies CORS, and injects security headers. | Does NOT execute business logic or maintain relational database state. |
| **`config-server`** | Centralized Configuration Management | Serves versioned YAML configuration files to microservices on startup via Spring Cloud Config native profile. | Does NOT process client requests or execute runtime business workflows. |
| **`discovery-server`** | Dynamic Service Registry | Tracks live instances, IP addresses, and ports of all microservices via Eureka heartbeats. | Does NOT proxy HTTP traffic or store application data. |
| **`frontend`** | User Interface & Real-Time Observability | Guides users through ingestion, schema mapping, live SSE worker dashboards, and non-destructive CSV/XLSX export. | Does NOT store credentials or bypass the API Gateway in production. |

---

## 2. Detailed Bounded Contexts

### A. `dataset-service` (Port 9743)
* **Core Principle**: *"Dataset Service owns dataset ingestion, batch job execution, and relational persistence."*
* **Key Responsibilities**:
  1. **Batch Job Submission (`/api/v1/enrichment/jobs`)**: Accepts row payloads and column mappings, assigns a unique `jobId`, associates the job with the authenticated `userId`, and assigns tasks to the worker pool.
  2. **Bounded Concurrency Orchestration**: Runs tasks on a managed `ThreadPoolTaskExecutor` (default: 3 workers) with a 500-item queue and `CallerRunsPolicy`.
  3. **Row-Level Error Isolation**: Network timeouts or scraping errors on one row are caught and flagged as `FAILED` or `PARTIAL`, allowing remaining rows to complete uninterrupted.
  4. **Real-Time SSE Streaming (`/api/v1/enrichment/jobs/{jobId}/events`)**: Streams discrete row lifecycle events (`STARTED`, `RESEARCH`, `AI_EXTRACTION`, `PERSISTENCE`, `COMPLETED`, `FAILED`) with an in-memory replay buffer.
  5. **User-Scoped Relational Persistence (`/api/v1/entities`)**: Persists canonical entity records, discovered sources, and attribute evidence into MySQL via Spring Data JPA and versioned Flyway migrations.

### B. `research-service` (Port 9741)
* **Core Principle**: *"Research produces evidence."*
* **Key Responsibilities**:
  1. **Seed Normalization**: Strips tracking parameters (`utm_*`, `ref`), unwraps markdown links, and canonicalizes URLs.
  2. **Requirement-Aware Search Querying**: Transforms target entities and field requirements into optimized queries using `QueryBuilder`.
  3. **Multi-Source Discovery & Ranking**: Discovers candidate pages via `SearchProvider` (Tavily with graceful `MockSearchProvider` fallback) and ranks authoritative primary domains.
  4. **Defensive Web Crawling**: Fetches content with strict 5,000ms timeouts, 500KB buffer limits, and Jsoup HTML boilerplate removal. Falls back to search snippets if target sites return anti-bot 403 or 999.
  5. **Evidence Compilation & Corroboration**: Consolidates claims from multiple sources, detects contradictions, and computes confidence tiers (`HIGH`, `MEDIUM`, `LOW`).

### C. `ai-intelligent-service` (Port 9742)
* **Core Principle**: *"AI produces clean, structured, requirement-aware intelligence without hallucination."*
* **Key Responsibilities**:
  1. **Requirement Interpretation (`/api/v1/ai/requirement`)**: Translates natural language requirements into concrete target fields and search keywords.
  2. **Input Cleansing (`/api/v1/ai/clean`)**: Cleans noisy entity names, strips emojis, parses compound roles and titles, and normalizes URLs.
  3. **Grounded Fact Extraction (`/api/v1/ai/enrich`)**: Extracts structured facts from text. Enforces the **Zero-Hallucination Guardrail**: every fact candidate must have an `exactQuote` matching source text verbatim.
  4. **Profile Assessment (`/api/v2/ai/profile/assess`)**: Computes multi-dimensional fit scores, priority ratings, and recommended engagement strategies against defined objectives.
  5. **Transparent Fallback**: When LLMs are unreachable or rate-limited, execution automatically falls back to deterministic heuristic engines (`DeterministicAiIntelligence`).

### D. `auth-service` (Port 9739)
* **Core Principle**: *"Auth Service owns user identity, credential security, and JWT lifecycle."*
* **Key Responsibilities**:
  1. **Registration & Password Hashing**: Validates email uniqueness and hashes passwords with BCrypt (strength 10).
  2. **Token Issuance**: Generates signed HMAC-SHA256 JWT tokens containing `userId` and `email` claims.
  3. **User Profile Retrieval (`/api/v1/auth/me`)**: Validates token and returns current user identity.

### E. `api-gateway` (Port 9738)
* **Core Principle**: *"API Gateway is the single unified entry point enforcing security, routing, and identity integrity."*
* **Key Responsibilities**:
  1. **Reverse Proxy Routing**: Routes requests dynamically to downstream services.
  2. **JWT Authentication Boundary**: Validates Bearer tokens on protected endpoints.
  3. **Anti-Spoofing Header Normalization**: Strips client-supplied `X-User-Id` and `X-User-Email` headers, injecting cryptographically verified headers downstream only after verifying token validity.
  4. **CORS & Security Headers**: Manages preflight requests and injects strict security headers (`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`).

### F. `config-server` (Port 9736) & `discovery-server` (Port 9737)
* **`config-server`**: Native file-based Spring Cloud Config Server loading YAML files from `config/`. If unreachable, services cleanly fall back to local `application.yaml`.
* **`discovery-server`**: Spring Cloud Netflix Eureka Server maintaining heartbeat-backed service registrations, enabling dual resolution (`lb://SERVICE-NAME` or direct container DNS).

### G. `frontend` (Port 3000)
* **Core Principle**: *"Frontend delivers transparent execution observability and evidence inspection."*
* **Key Responsibilities**:
  1. **In-Browser Parsing**: Parses CSV and XLSX files locally using SheetJS (`xlsx`), avoiding temporary server-side storage of raw files.
  2. **Live Execution Dashboard**: Real-time worker cards, progress bars, and activity logs powered by SSE.
  3. **Evidence Verification Modal**: Allows inspecting verbatim evidence quotes, provenance URLs, and confidence ratings while remaining workers are still processing.
  4. **Non-Destructive Export**: Appends enriched columns (`Enriched_<field>`, `Canonical_Url`, `Enrichment_Status`) while preserving all original spreadsheet columns and values.
