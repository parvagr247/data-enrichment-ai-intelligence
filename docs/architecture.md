# System Architecture

The **Data Enrichment AI Intelligence Platform** is a distributed, production-grade enrichment engine that transforms sparse, noisy, or unstructured entity inputs into verified, structured, requirement-aware profiles backed by verbatim evidence quotes and multi-source confidence tiers.

---

## 1. High-Level Topology

```mermaid
flowchart TB
    subgraph UI ["Client Layer (Port 3000)"]
        Frontend["Next.js 15 Web Application<br/>- File Upload (CSV/XLSX)<br/>- Column Detection & Mapping<br/>- Requirement Input & Chips<br/>- Live Progress & Table<br/>- Grounded Evidence Inspector<br/>- CSV/XLSX Exporter"]
    end

    subgraph Backend ["Microservices Layer (Spring Boot / Java 25)"]
        DatasetService["dataset-service (:9743)<br/>- Batch Enrichment Jobs<br/>- Row State Tracking<br/>- Error Isolation<br/>- Entity Persistence<br/>- Flyway Migrations"]
        ResearchService["research-service (:9741)<br/>- Entity Identity & Normalization<br/>- Query Formulation<br/>- Multi-Source Web Discovery<br/>- Web Fetching & Cleaning<br/>- Evidence Extraction<br/>- Multi-Source Corroboration"]
        AIService["ai-intelligent-service (:9742)<br/>- Spring AI & Gemini Client<br/>- Requirement Interpretation<br/>- Input Data Cleansing<br/>- Fact Extraction & Grounding<br/>- Anti-Hallucination Guardrails"]
    end

    subgraph Storage ["Persistence Layer (Port 3306)"]
        MySQL[("MySQL 8.0+<br/>- entities<br/>- entity_sources<br/>- entity_attributes<br/>- flyway_schema_history")]
    end

    subgraph External ["External Providers"]
        SearchAPI["Web Search Engine<br/>(Tavily / Mock Provider)"]
        WebPages["Discovered Web Pages / URLs"]
        LLMProvider["LLM API<br/>(Google Gemini / Vertex)"]
    end

    Frontend -->|HTTP REST| DatasetService
    Frontend -->|HTTP REST| ResearchService
    DatasetService -->|HTTP REST Client| ResearchService
    DatasetService -->|HTTP REST Client| AIService
    DatasetService -->|JDBC JPA| MySQL
    ResearchService -->|HTTP REST Client| AIService
    ResearchService -->|HTTP REST Client| DatasetService
    ResearchService -->|HTTPS| SearchAPI
    ResearchService -->|HTTPS Fetch| WebPages
    AIService -->|HTTPS| LLMProvider
```

---

## 2. Microservice Responsibilities & Core Principles

The platform is strictly organized around clear separation of concerns:

### A. `research-service` (Port 9741)
* **Core Principle**: *"Research produces evidence."*
* **Primary Responsibilities**:
  1. **Seed Input Identification & Normalization**: Canonicalizes raw URLs and handles sparse seeds across entity types (`PERSON`, `ORGANIZATION`, `PRODUCT`, `REPOSITORY`, `WEBSITE`, `OTHER`).
  2. **Requirement-Aware Search Query Formulation**: Combines entity identifiers and target field requirements into optimized boolean/quoted search queries.
  3. **Multi-Source Discovery & Primary Source Ranking**: Queries search providers (Tavily with graceful Mock fallback) and prioritizes authoritative primary domains (official domains, GitHub, LinkedIn, Docs).
  4. **Web Content Fetching & Boilerplate Cleaning**: Fetches HTML/JSON/Markdown content, cleans noise, navigation links, and ads, extracting core text sections.
  5. **Evidence Extraction**: Performs dual-strategy extraction:
     - Specialized deterministic extractors for known patterns.
     - Delegation to `ai-intelligent-service` for nuanced context.
  6. **Multi-Source Corroboration & Conflict Resolution**: Merges overlapping evidence, flags conflicting claims across sources, and computes confidence tiers (`HIGH`, `MEDIUM`, `LOW`).

### B. `ai-intelligent-service` (Port 9742)
* **Core Principle**: *"AI produces clean, structured, requirement-aware enrichment without hallucination."*
* **Primary Responsibilities**:
  1. **Requirement Interpretation (`/api/v1/ai/requirement`)**: Parses free-form natural language requirements (e.g., *"Find tech stack, founders, and latest funding"*) into structured target fields, focus areas, and priority search keywords.
  2. **Input Cleansing (`/api/v1/ai/clean`)**: Cleans messy names, strips emojis, parses compound roles and titles, and normalizes URLs.
  3. **Fact Extraction & Grounding (`/api/v1/ai/enrich`)**: Extracts structured factual tuples from raw scraped text. Every extracted attribute **must** be grounded in an exact, verbatim text quote. If evidence is missing, the value must be left empty or marked `UNKNOWN`.
  4. **Resilient Heuristic Fallback**: When external LLM APIs are unreachable or rate-limited, an offline deterministic heuristic engine provides continuous operation.

### C. `dataset-service` (Port 9743)
* **Core Principle**: *"Dataset Service owns dataset, job, and persistence concerns."*
* **Primary Responsibilities**:
  1. **Batch Enrichment Job Execution (`/api/v1/enrichment/jobs`)**: Orchestrates row-by-row asynchronous processing over bounded thread pools (`enrichmentJobExecutor`).
  2. **Row-Level Error Isolation**: Network timeouts, missing web pages, or parsing errors on an individual row are isolated—the row is marked `FAILED` with diagnostic messages while the remaining batch continues unhindered.
  3. **Direct Single-Record Enrichment (`/api/v1/enrichment/single`)**: Provides synchronous row enrichment bridging client mapping, research, and persistence.
  4. **Relational Entity Persistence (`/api/v1/entities`)**: Persists canonical entity summaries, discovered source URLs, and multi-source attributes into MySQL with Flyway migration versioning.

### D. `frontend` (Port 3000)
* **Core Principle**: *"Frontend guides the user through progressive enrichment and transparently displays grounded evidence."*
* **Primary Responsibilities**:
  1. **Progressive 5-Stage Workflow**: Upload $\rightarrow$ Preview $\rightarrow$ Column Mapping $\rightarrow$ Live Processing $\rightarrow$ Results & Export.
  2. **Requirement Guidance**: Suggestion chips and flexible requirement input for domain-targeted enrichment.
  3. **Evidence Transparency**: Attribute confidence badges, conflict indicators, and detailed evidence inspection modal showing exact quotes and provenance URLs.
  4. **Non-Destructive Export**: Combines original spreadsheet columns with `Enriched_*` attributes, status, and canonical URLs into clean CSV/XLSX files.

---

## 3. Communication Contracts & Security

* **Communication Protocol**: Synchronous HTTP/1.1 REST using JSON (`application/json`) contracts.
* **CORS**: Configured uniformly across all 3 backend services allowing origins `http://localhost:3000`, `http://127.0.0.1:3000`, and Docker internal network hostnames.
* **Security Model**: Zero Spring Security overhead for rapid, frictionless local development and containerized orchestration.

---

## 4. V2 Architecture Evolutions (Tasks 51–80)

The V2 platform introduces three modular architectural enhancements:

### 1. Research & Discovery Engine (Tasks 51–60)
* **Provider Abstraction**: Decoupled `ResearchProvider` / `SearchProvider` interfaces supporting production web search (Tavily) and zero-network test mocks (`MockSearchProvider`).
* **Multi-Strategy Query Generation**: Formulates targeted queries per intent (`QueryIntent`, `QueryStrategy`, `ResearchQuery`). The primary identity query (`EXACT_NAME_AND_ORG`) is always anchored first, followed by adaptive queries targeting missing schema fields.
* **Source Classification & Deduplication**: Canonicalizes and strips tracking parameters from URLs via `SourceDeduplicator`. Categorizes domains via `SourceTypeClassifier` into strongly-typed `SourceType` and baseline `SourceReliability` tiers.
* **Multi-Factor Source Ranking**: Prioritizes sources via `SourceRanker` combining search engine relevance, authoritative source type weights, entity name matching, and domain reliability.

### 2. Evidence & Extraction 2.0 (Tasks 61–70)
* **Cohesive Field Extractors**: Replaced monolithic extraction logic with specialized single-responsibility classes:
  - `RoleFieldExtractor`: Extracts executive and technical positions.
  - `OrganizationFieldExtractor`: Identifies companies and institutional affiliations.
  - `EducationFieldExtractor`: Identifies degrees, academic majors, and universities.
  - `LocationFieldExtractor`: Identifies geographic headquarters, cities, and countries.
  - `TechFieldExtractor`: Identifies programming languages, frameworks, and technologies.
* **Verbatim Evidence Tuples**: Every extracted fact is anchored by an immutable `EvidenceTuple` containing `exactQuote`, `sourceUrl`, and `confidence`.
* **Multi-Source Corroboration & Conflict Resolution**: `EvidenceMerger` boosts confidence when multiple sources corroborate a claim, and non-destructively records `conflictDetected = true` with detailed `conflictDescription` when contradictory values are encountered.

### 3. Spring AI Intelligence Layer (Tasks 71–80)
* **Externalized Prompt Engineering**: Templates stored in `src/main/resources/prompts/*.st` rendered dynamically by `PromptTemplateService`.
* **Zero-Hallucination Guardrails**: `AiResponseValidator` validates that every claim's exact quote exists verbatim within the retrieved source text, discarding ungrounded attributes.
* **Resilient Fallback & Bounded Retries**: `SpringAiIntelligence` executes up to 2 retries with exponential backoff before gracefully failing over to `DeterministicAiIntelligence`. The system never returns an unhandled HTTP 500 error on remote AI outages.
* **Execution Telemetry**: Captures token consumption, latencies, provider details, and execution status through `AiExecutionMetrics`.

---

## 5. Bounded Parallel Processing & Rich Profile Synthesis

The platform addresses sequential bottlenecks and shallow data profiles through two major architectural enhancements:

### 1. Bounded Row-Level Concurrency (`dataset-service`)
* **Dedicated Task Executor (`EnrichmentTaskExecutor`)**: Custom `ThreadPoolTaskExecutor` with bounded concurrency (default: 3 workers, configurable via `enrichment.concurrency.workers`), bounded work queue (capacity: 500), and `CallerRunsPolicy` backpressure protection against queue starvation.
* **Row-Level Failure Isolation**: Each row executes in an isolated `CompletableFuture` task. Transient errors, missing links, or network timeouts for one row never fail adjacent rows or abort the overall batch job.
* **Thread-Safe Job Telemetry**: Atomic progress counters track completed, failed, and in-flight rows. Final job status seamlessly reflects outcome: `COMPLETED`, `PARTIAL` (when some rows succeed and some fail), `FAILED`, or `CANCELLED`.
* **Job Cancellation Lifecycle**: `POST /api/v1/enrichment/jobs/{jobId}/cancel` allows operators to stop queued tasks instantly while allowing executing worker threads to finish gracefully.

### 2. Rich Evidence Extraction & Professional Profile Synthesis (`research-service` & `ai-intelligent-service`)
* **Structured Field Extractors**: Specialized evidence extractors parse deep professional dimensions with verbatim quote grounding:
  - `ExperienceFieldExtractor`: Identifies chronological role timelines, company affiliations, and date ranges.
  - `EducationFieldExtractor`: Identifies degrees, academic majors, institutions, and graduation years.
  - `SkillFieldExtractor`: Extracts, normalizes, and deduplicates technical skills and core competencies.
  - `ProjectFieldExtractor`: Identifies open-source repositories and notable project initiatives.
  - `ActivityFieldExtractor`: Differentiates between `AUTHORED` articles/posts and passive `MENTIONS`.
* **Thread-Safe Source Memoization (`ThreadSafeSourceCache`)**: URL fetch cache eliminates redundant HTTP calls when multiple rows reference the same domain or profile URLs.
* **Multi-Tab Inspection & Rich Export (`frontend`)**: Next.js frontend delivers a tabbed record inspection modal (Overview, Experience, Education, Skills, Projects, Activity, All Evidence, Sources) and flattened multi-attribute CSV/XLSX export.

