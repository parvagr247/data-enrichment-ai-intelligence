# Research Service

Production-grade entity discovery, content extraction, and grounded evidence enrichment microservice.

---

## 1. What This Service Does

The **Research Service** is responsible for autonomous, grounded entity intelligence gathering across the web and enterprise data repositories:
- **Canonical Identity Resolution:** Normalizes dirty input URLs, cleans tracking parameters, and computes deterministic SHA-256 entity hashes.
- **Multi-Source Discovery:** Queries external search providers (e.g. Tavily or local mock) to discover official websites, repositories, documentation, and profiles.
- **Content Ingestion & Cleansing:** Safely fetches web resources with built-in SSRF protection, parses HTML/JSON via Jsoup, strips boilerplate/navigation, and produces structured documents.
- **Heuristic & AI Evidence Extraction:** Resolves entities to pages and extracts structured evidence tuples with confidence tiers (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`), snippets, and corroborating citations.
- **Asynchronous Job Scheduling:** Supports synchronous REST execution (`POST /api/v1/research`) as well as bounded thread pool asynchronous execution (`POST /api/v1/research/jobs`, `GET /api/v1/research/jobs/{id}`).
- **Resilient Persistence Integration:** Emits enriched entity snapshots to `dataset-service` via non-blocking HTTP REST integration.

---

## 2. Package Structure & Architectural Boundaries

`research-service` is organized into **7 distinct, cohesive architectural boundaries** within a single deployable Spring Boot module:

```text
com.subdual.research_service/
├── api/                  # Inbound REST layer
│   ├── ResearchController.java
│   └── dto/              # Public JSON contracts (Request, Response, Evidence, Job)
├── research/             # Core business boundary & lifecycle orchestration
│   ├── ResearchService.java
│   ├── ResearchOrchestrator.java
│   ├── ResearchJobService.java
│   ├── InMemoryResearchJobService.java
│   ├── model/            # Immutable domain representations (records & enums)
│   └── pipeline/         # Intention-revealing pipeline steps, diagnostics & timer
├── discovery/            # Strategy-based search query builder and provider clients
│   ├── service/          # Discovery service contract and orchestration
│   │   ├── ResearchDiscoveryService.java
│   │   └── DefaultResearchDiscoveryService.java
│   ├── provider/         # Strategy abstraction & search provider implementations
│   │   ├── SearchProvider.java
│   │   ├── TavilySearchProvider.java
│   │   └── MockSearchProvider.java
│   └── QueryBuilder.java # Shared discovery query generation
├── extraction/           # HTML extraction, entity resolution, evidence harvesting
│   ├── ExtractedDocument.java
│   ├── ContentExtractor.java
│   ├── EntityResolver.java
│   ├── EvidenceExtractor.java
│   └── SourceEvidenceService.java
├── integration/          # Outbound network integration clients
│   ├── web/              # SSRF-guarded HTTP fetcher and FetchedContent
│   ├── ai/               # REST client for downstream ai-intelligent-service
│   └── persistence/      # REST client & persister for dataset-service
├── config/               # Type-safe @ConfigurationProperties and Spring @Configuration
│   ├── ResearchConfiguration.java
│   ├── ResearchDiscoveryProperties.java
│   ├── ResearchPipelineProperties.java
│   ├── WebFetchProperties.java
│   └── ServiceMeshProperties.java
└── common/               # Shared cross-cutting concerns
    ├── exception/        # BusinessRuleException, ExternalServiceException, RFC 7807 handler
    └── validation/       # Request validation rules
```

---

## 3. High-Level Flow

Every research operation follows a clean, 7-step sequential pipeline coordinated by `ResearchOrchestrator`:

```
ResearchRequest
   │
   ▼
[1. Validate]           ──> Enforces URL format, entity type, and required fields
   │
   ▼
[2. Normalize]          ──> Strips tracking query params, derives canonical URN/URL, computes SHA-256 ID
   │
   ▼
[3. Discover]           ──> Formulates domain search query; fetches candidate URLs via SearchProvider
   │
   ▼
[4. Process Sources]    ──> Deduplicates, classifies domain category, evaluates composite quality score
   │
   ▼
[5. Extract Evidence]   ──> SSRF-safe content fetch -> DOM cleanup -> Entity resolution -> Evidence tuples
   │
   ▼
[6. Persist Snapshot]   ──> Asynchronous / non-blocking persistence to dataset-service (resilient to failure)
   │
   ▼
[7. Assemble Response]  ──> Binds metadata (timing, counts, provider), sets status (COMPLETED/PARTIAL)
   │
   ▼
ResearchResponse
```

---

## 4. Entity Accuracy, URL-First Anchoring & Noise Rejection

Web research pipelines often suffer from identity cross-contamination and fragile web scraping. The Research Service employs a multi-tiered strategy to guarantee grounded, high-precision results:

### 4.1 URL-First Research & Targeted Discovery
When a request provides a specific profile or repository URL (e.g., `https://linkedin.com/in/jane-doe-tech` or `https://github.com/org/repo`):
1. **Primary Identity Anchor:** The supplied URL is treated as the primary anchor for the target entity.
2. **Anchor-Bound Queries:** `QueryBuilder` formulates focused search queries combining entity name, host, and profile slug (e.g., `"Jane Doe" linkedin.com/in/jane-doe-tech`) rather than broad name searches (`Jane Doe`).
3. **Deterministic Score Priority:** `DeterministicRelevanceEvaluator` and `SourceProcessor` ensure the anchor URL receives the highest relevance score (1.00) and rank 0.

### 4.2 Multi-Signal Entity Resolution & Anti-Contamination
Common names (e.g. "Jane Doe") produce hundreds of conflicting public profiles across unrelated fields (dentistry, acting, academia, software engineering). Merely checking if the person's name appears in candidate page text leads to data contamination.

`EntityResolver` prevents contamination through multi-signal verification:
- **Canonical Match:** Page URL matches the primary target anchor (`HIGH` confidence).
- **Multi-Tenant Slug Match:** On platforms like LinkedIn, GitHub, or Twitter, the path/slug (e.g., `/in/jane-doe-tech`) must match the target identifier; generic same-host URLs are not treated as matches.
- **Corroborating Metadata:** Secondary sources (company pages, conference bios) must match contextual signals (e.g., organization `CloudScale Inc`, title `Senior Software Engineer`, location, education).
- **Negative Profession Filtering:** Discards pages containing conflicting profession keywords (e.g. dentist, clinic, actress, filmography) when resolving technical/corporate targets.
- **Strict Exclusion:** Discovered pages that only match the entity's name without corroborating signals are rejected (`matched = false`) and completely excluded from evidence extraction.

### 4.3 Inaccessible Source & LinkedIn HTTP 999 Resilience
Bot-protected platforms (notably LinkedIn returning `HTTP 999` or anti-scraping challenges) frequently block automated fetchers. Rather than failing the pipeline or fabricating synthetic data:
1. **Diagnostic Warning:** The fetcher records an informative `PRIMARY_INACCESSIBLE` event in `ResearchDiagnostics` without triggering degraded pipeline status.
2. **Provenance Retention:** The primary anchor URL remains in `rankedSources` so downstream consumers can inspect the intended canonical source.
3. **No False Fabrication:** No fake or hallucinated attributes are created from unreadable HTML.
4. **Secondary Corroboration:** The pipeline continues extracting evidence from verified secondary sources (company team pages, technical blogs, press releases).
5. **Completed Status:** When secondary sources yield verified evidence, the research status completes cleanly as `COMPLETED`.
6. **Multi-Source Corroboration:** When multiple independent sources confirm an attribute (e.g. role or employer), confidence is upgraded to `HIGH` and all confirming URLs are appended to `corroboratingSources`.

---

## 5. Why We Chose a Single Module

Rather than prematurely splitting this service into multiple Maven submodules (e.g. `research-api`, `research-core`, `research-infra`), we adopted a **modular-monolith architecture within a single Spring Boot service**:
1. **Zero Multi-Module Overhead:** Eliminates complex Maven pom interdependencies, circular references, version skew, and duplicate plugin configurations.
2. **Fast Development & Build Cycles:** Compilation, test execution (`mvn test`), and container packaging remain instantaneous.
3. **Rigorous Encapsulation Without Bureaucracy:** Package-private visibility and cohesive package boundaries provide strong architectural isolation without runtime friction.
4. **Single Unit of Deployment:** Directly builds into a single self-contained executable JAR and Docker image.

---

## 6. Key Design Principles

- **Constructor Injection:** No `@Autowired` on private fields; all beans use constructor injection.
- **Immutability First:** Domain state, contracts, and intermediate values are immutable Java `record`s.
- **Intention-Revealing Methods:** Step methods in `ResearchOrchestrator` are short (5–15 lines), clearly communicating *what* is happening at every stage.
- **Resilience & Non-Blocking Fallback:** Outbound HTTP failures to `dataset-service` or `ai-intelligent-service` log warnings and degrade to `PARTIAL` status rather than crashing user requests.
- **Zero Hallucination / Evidence Provenance:** Every extracted attribute explicitly cites its origin URL, exact textual snippet, and confidence tier.
- **Security / SSRF Hardening:** `WebContentFetcher` strictly rejects non-HTTP protocols, private IP ranges (RFC 1918), localhost/loopback, and internal container hostnames.

---

## 7. How to Run & Test

### Local Maven Commands
```bash
# Compile and check dependencies
mvn clean compile -DskipTests

# Run the complete test suite (106 tests)
mvn test

# Package standalone executable JAR
mvn clean package -DskipTests
```

### Docker Execution
```bash
# Build and start container via root compose
docker compose -f infrastructure/docker/docker-compose-dev-all.yml up -d --build research-service

# Check service logs
docker compose -f infrastructure/docker/docker-compose-dev-all.yml logs -f research-service
```

---

## 8. Configuration Reference

Configuration properties defined in `application.yml`:

| Property | Default | Description |
| :--- | :--- | :--- |
| `research.discovery.provider` | `mock` | Active search provider (`mock` or `tavily`) |
| `research.discovery.api-key` | `${TAVILY_API_KEY:}` | API key for Tavily search provider |
| `research.discovery.max-results` | `5` | Maximum candidate search results per query |
| `research.pipeline.max-sources` | `5` | Maximum ranked sources to ingest and extract |
| `research.pipeline.max-content-length` | `50000` | Maximum clean text character limit per document |
| `research.fetch.connect-timeout-ms` | `3000` | Web content fetch connection timeout |
| `research.fetch.read-timeout-ms` | `5000` | Web content fetch read timeout |
| `research.fetch.max-redirects` | `5` | Maximum HTTP redirect hops permitted |
| `service-mesh.dataset-service-url` | `http://localhost:9743` | Endpoint for downstream dataset persistence |
| `service-mesh.ai-service-url` | `http://localhost:9742` | Endpoint for downstream AI extraction service |
