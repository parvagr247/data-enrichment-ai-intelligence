# Production Failure Modes & Post-Mortems

This document provides a deep, production-grade engineering analysis of the 11 critical failure modes encountered, debugged, and architecturally resolved in the **Data Enrichment AI Intelligence Platform**.

Each post-mortem follows standard Tier-1 engineering post-mortem structure:
1. **Summary & Error Signature**
2. **Root Cause Analysis (RCA)**
3. **Reproducing Scenario & Production Logs**
4. **Engineering Fix Applied**
5. **Verification & Regression Safeguards**
6. **System Lessons & Senior Engineering Takeaways**

---

## 1. Gemini Model Name Deprecation & Spring AI Autoconfiguration (`gemini-2.5-flash` 404)

### 1. Summary & Error Signature
During automated AI extraction and enrichment requests, the AI Intelligent Service (`ai-intelligent-service:9742`) failed with `404 Not Found`, citing that `models/gemini-2.5-flash` was non-existent in the Gemini API `v1beta` namespace.

```
[AI_HTTP_ERROR] Upstream Gemini API error: 404 Not Found: "models/gemini-2.5-flash is not found for API version v1beta"
org.springframework.web.client.HttpClientErrorException$NotFound: 404 Not Found: [models/gemini-2.5-flash is not found for API version v1beta]
```

### 2. Root Cause Analysis
Google DeepMind/Google Cloud periodically releases model checkpoints under versioned naming schemes. The configuration in `application.yml` had been pinned to a preview checkpoint name `gemini-2.5-flash` that was retired or never promoted to public GA in `v1beta`. Because Spring AI's autoconfiguration initializes the `ChatClient` bean at application boot using whatever property is configured under `spring.ai.gemini.chat.options.model`, the service successfully started, but every inference invocation failed at runtime when dispatching HTTP POST requests to Google's backend.

### 3. Reproducing Scenario & Production Logs
```
2026-09-06T09:41:10.120Z [pool-2-thread-1] WARN  c.s.a.service.GeminiAiExtractionService - [AI_EXTRACTION_SKIPPED] AI extraction request to http://ai-intelligent-service:9742 failed: 404 Not Found: "models/gemini-2.5-flash is not found for API version v1beta"
```

### 4. Engineering Fix Applied
Updated `apps/backend/ai-intelligent-service/src/main/resources/application.yml` to use the current GA, production-ready lightweight model:
```yaml
spring:
  ai:
    gemini:
      chat:
        options:
          model: ${GEMINI_MODEL:gemini-2.0-flash}
          temperature: 0.1
```
Additionally added parameterization via `${GEMINI_MODEL:gemini-2.0-flash}` to allow runtime environment overrides in `docker-compose.yml` without requiring code rebuilds.

### 5. Verification & Regression Safeguards
- Unit and integration tests in `ai-intelligent-service` (`AiExtractionControllerTest`, `ProfileAssessmentControllerTest`) verify that the controller responds with structured JSON.
- Added integration test verifying model options fallback and fallback safety in `GeminiAiExtractionServiceTest`.

### 6. System Lessons & Senior Engineering Takeaways
- **Fail Fast vs Deferred Failure**: Model names should be verified with a lightweight health-check / ping during application warmup or startup actuator probes, rather than discovering a 404 on the first user request.
- **Config Externality**: Third-party AI model identifiers must always be parameterized via environment variables with safe, tested default fallbacks.

---

## 2. Silent HTTP Fallback Dishonesty (`COMPLETED` status masking upstream AI outage)

### 1. Summary & Error Signature
When `ai-intelligent-service` failed or timed out, the dataset enrichment pipeline silently caught the exception, executed a deterministic rule-based fallback, and marked the entity row as `COMPLETED`. The user received a row with empty or `UNKNOWN` attributes while the UI showed a green "COMPLETED" badge, destroying trust in data provenance.

### 2. Root Cause Analysis
The orchestrator in `DefaultDatasetEnrichmentService` had a naive status decision block:
```java
// BEFORE:
String status;
if ("FAILED".equalsIgnoreCase(researchResp.status())) {
    status = "FAILED";
} else if ("PARTIAL".equalsIgnoreCase(researchResp.status()) || !unresolvedFields.isEmpty()) {
    status = "PARTIAL";
} else {
    status = "COMPLETED"; // Masked AI outages!
}
```
If research succeeded in locating sources, but the AI service was down or threw 500s, `aiResp` was null. The code populated attributes directly from raw research tuples and reported `COMPLETED`.

### 3. Reproducing Scenario & Production Logs
```
[AI_EXTRACTION_SKIPPED] AI extraction request to http://ai-intelligent-service:9742 failed: Connection Refused
Persisting canonical profile (1 attributes) to catalog...
Completed enrichment job 'job-1' in 420 ms (completed=1, partial=0, failed=0)
```
The user inspected the row: all AI-synthesized fields (`careerBackground`, `relevantProjects`, `dimensions`) were missing, yet the job reported 100% completion.

### 4. Engineering Fix Applied
1. Established an honest, explicit 5-tier status taxonomy:
   - `COMPLETED`: Full research and successful AI intelligence synthesis.
   - `AI_DEGRADED`: Sources and evidence were gathered, but AI service was unavailable/failed; deterministic fallback was utilized.
   - `INSUFFICIENT_EVIDENCE`: Sources returned 0 relevant documents or all attributes were ungrounded/unknown.
   - `PARTIAL`: Completed with some requested target fields unresolved.
   - `FAILED`: Hard upstream network failure or identity resolution error.
2. Updated `DefaultDatasetEnrichmentService.java`:
```java
boolean insufficientEvidence = "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(researchResp.status())
        || "NO_SOURCES".equalsIgnoreCase(researchResp.status())
        || (sourceUrls.isEmpty() && finalAttributes.isEmpty() && !"COMPLETED".equalsIgnoreCase(researchResp.status()));

boolean aiDegraded = (aiResp == null && profileAssessment == null && !evidenceMap.isEmpty() && (requirement != null && !requirement.isBlank()));

String status;
if ("FAILED".equalsIgnoreCase(researchResp.status())) {
    status = "FAILED";
} else if (insufficientEvidence) {
    status = "INSUFFICIENT_EVIDENCE";
} else if (aiDegraded) {
    status = "AI_DEGRADED";
} else if ("PARTIAL".equalsIgnoreCase(researchResp.status()) || !unresolvedFields.isEmpty()) {
    status = "PARTIAL";
} else {
    status = "COMPLETED";
}
```
3. Upgraded frontend `EnrichedDatasetTable.tsx`, `EvidenceDetailModal.tsx`, and `ResearchProfileModal.tsx` to render amber `AI DEGRADED` badges and honest explanatory warning banners.

### 5. Verification & Regression Safeguards
- Unit test `shouldReportAiDegradedWhenAiServiceFailsWithEvidence()` explicitly mocks AI service returning `null` when evidence exists and verifies status is `AI_DEGRADED`.
- Unit test `shouldReportInsufficientEvidenceWhenResearchYieldsInsufficientEvidence()` verifies `INSUFFICIENT_EVIDENCE`.

### 6. System Lessons & Senior Engineering Takeaways
- **Graceful Degradation Must Be Observable**: Fallback mechanisms should keep the system available, but must *never* mask the degradation. Provenance transparency is non-negotiable in data engineering systems.

---

## 3. Upstream Content-Type & ProblemDetail Parsing Mismatch (`application/problem+json` & `octet-stream`)

### 1. Summary & Error Signature
In `research-service`, calls from `RestAiExtractionClient` to `ai-intelligent-service` threw unhandled `HttpMessageConverterExtractor` exceptions when the server responded with an error or non-standard Content-Type:

```
org.springframework.web.client.RestClientResponseException: Could not extract response: no suitable HttpMessageConverter found for response type [com.subdual.research_service.dto.AiExtractionResponse] and content type [application/problem+json]
```
and intermittently:
```
Could not extract response: no suitable HttpMessageConverter found for response type [com.subdual.research_service.dto.AiExtractionResponse] and content type [application/octet-stream]
```

### 2. Root Cause Analysis
1. Spring Boot 3 / RFC 7807 emits `application/problem+json` by default on controller advice exceptions.
2. Spring's `RestClient` with Jackson JSON converter only registers `application/json` and `application/*+json` by default if configured strictly. If the remote service returned `application/problem+json`, or if an upstream proxy / Docker gateway emitted an unformatted byte stream tagged as `application/octet-stream`, Jackson unmarshalling threw an uncaught exception, crashing the entire row pipeline.

### 3. Reproducing Scenario & Production Logs
```
2026-09-06T09:41:15.823Z ERROR [enrichment-worker-1] c.s.r.c.RestAiExtractionClient - AI extraction request to http://ai-intelligent-service:9742 failed: Could not extract response: no suitable HttpMessageConverter found for response type [...] and content type [application/problem+json]
```

### 4. Engineering Fix Applied
1. Configured `RestAiExtractionClient` with explicit headers accepting `application/json`, `application/problem+json`, and `*/*`.
2. Added custom `.onStatus(status -> status.isError(), (request, response) -> ...)` handler to catch 4xx/5xx responses before Jackson tries to unmarshal them into the success DTO.
3. In `ai-intelligent-service`, updated global exception handlers to explicitly set `MediaType.APPLICATION_JSON` in the response entity headers.

```java
// RestAiExtractionClient.java
return restClient.post()
        .uri("/api/v1/ai/extract")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("application/problem+json"))
        .body(request)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, resp) -> {
            String errorBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
            log.warn("[AI_HTTP_ERROR] AI extraction HTTP {} {}: {}", resp.getStatusCode(), resp.getStatusText(), errorBody);
            throw new AiExtractionException("AI service returned " + resp.getStatusCode() + ": " + errorBody);
        })
        .body(AiExtractionResponse.class);
```

### 5. Verification & Regression Safeguards
- Unit test `RestAiExtractionClientTest.shouldHandleHttpErrorsGracefully()` verifies that 4xx and 5xx responses throw a typed `AiExtractionException` and trigger deterministic fallback rather than crashing the thread.

### 6. System Lessons & Senior Engineering Takeaways
- **HTTP Error Boundary Isolation**: Never let client unmarshallers attempt to parse error payloads into domain response DTOs. Separate HTTP status checking from body deserialization.

---

## 4. LinkedIn Bot Defense & HTTP 999 Request Denied

### 1. Summary & Error Signature
When attempting to enrich professional entities from LinkedIn profile URLs (e.g. `https://www.linkedin.com/in/saloni-sharma-872379180`), the crawler logged:
```
Using search provider snippet fallback for blocked LinkedIn sources
HTTP error fetching URL. Status=999, URL=https://www.linkedin.com/in/saloni-sharma-872379180
```

### 2. Root Cause Analysis
LinkedIn employs aggressive edge security (Cloudflare / custom bot management) that inspects TLS fingerprints, user-agents, IP reputation, and browser integrity. Direct HTTP GET requests via Jsoup or `HttpClient` immediately return HTTP status `999 Request Denied`. The crawler cannot access the HTML DOM of the profile page.

### 3. Reproducing Scenario & Production Logs
```
[Pipeline: CRAWLING]
Fetching page content from: https://www.linkedin.com/in/saloni-sharma-872379180
WARN c.s.r.s.DefaultSourceEvidenceService - Failed scraping https://www.linkedin.com/in/saloni-sharma-872379180: HTTP 999
[FALLBACK] Using search provider snippet fallback for blocked LinkedIn sources
```

### 4. Engineering Fix Applied
1. Implemented **Search Provider Snippet Fallback**: When scraping yields HTTP 999 or 403, the pipeline automatically falls back to the high-density snippet cached by Tavily/Mock search engine during discovery.
2. Tagged the resulting evidence with `extractionMethod: SEARCH_SNIPPET` instead of `FULL_PAGE`.
3. Adjusted confidence scores: full-page extractions receive `HIGH` confidence, whereas snippet extractions cap at `MEDIUM` unless corroborated by a second independent domain (e.g., GitHub, personal blog).

```java
// DefaultSourceEvidenceService.java
if (scrapeFailed && candidate.snippet() != null && !candidate.snippet().isBlank()) {
    log.info("Using search provider snippet fallback for blocked source: {}", candidate.url());
    return new ExtractedDocument(
            candidate.url(),
            candidate.title(),
            candidate.snippet(),
            ExtractionMethod.SEARCH_SNIPPET,
            candidate.sourceType()
    );
}
```

### 5. Verification & Regression Safeguards
- `SourceEvidenceServiceTest.shouldFallbackToSnippetWhenPageScrapeIsBlocked()` tests that HTTP 999 from Jsoup cleanly falls back to snippet text without losing evidence.

### 6. System Lessons & Senior Engineering Takeaways
- **The Modern Web is Walled**: Scraping social networks directly in production is inherently brittle. Resilient intelligence pipelines rely on search engine indices, API integrations, and multi-source corroboration rather than raw headless scraping.

---

## 5. Search Provider Snippet Fallback & Extraction Method Provenance

### 1. Summary & Error Signature
When snippet fallback was used, downstream AI extraction was previously presented with truncated snippets (100–200 characters), leading the LLM to hallucinate full career histories or infer job responsibilities not supported by the snippet text. Furthermore, the downstream user had no way of knowing whether an attribute came from an actual profile page or a 2-line Google/Tavily snippet.

### 2. Root Cause Analysis
Lack of provenance metadata in the domain model. `ExtractedDocument` and `EntitySourceDto` lacked an `extractionMethod` property. The LLM prompt was given raw text without clarifying whether it was full-page content or a truncated search snippet.

### 3. Reproducing Scenario & Production Logs
The AI extracted: `"Current Role: Senior Staff Architect"` based on a search snippet that merely said `"Saloni Sharma ... architecture enthusiast ... posted 3 weeks ago"`.

### 4. Engineering Fix Applied
1. Added `ExtractionMethod` enum (`FULL_PAGE`, `SEARCH_SNIPPET`) to:
   - `ExtractedDocument` in `research-service`
   - `EntitySourceDto` in `dataset-service`
   - `types/dataset.ts` in `frontend`
2. Enhanced the extraction prompt in `GeminiAiExtractionService`:
   ```
   If the evidence document is labeled [SEARCH_SNIPPET], you MUST ONLY extract facts explicitly written in the snippet text. DO NOT extrapolate career histories or current employer from snippet fragments.
   ```
3. Added visual indicators (`FULL PAGE` green pill vs `SEARCH SNIPPET` amber pill) in frontend modals (`EvidenceDetailModal.tsx`, `ResearchProfileModal.tsx`).

### 5. Verification & Regression Safeguards
- Unit tests verify that `ExtractedDocument.extractionMethod()` is correctly propagated through `EvidenceMerger` into `ResearchResult`.
- Frontend TypeScript build verifies `extractionMethod` type safety.

### 6. System Lessons & Senior Engineering Takeaways
- **Provenance Must Accompany Data**: An attribute value without extraction provenance is unverifiable. In AI pipelines, source metadata must describe *how* the text was obtained, not just *where*.

---

## 6. Single-Query Discovery Starvation vs Multi-Query Expansion

### 1. Summary & Error Signature
During identity discovery, the search provider was invoked with a single query formatted as:
```
'"Saloni" linkedin.com/in/saloni-sharma-872379180'
```
Tavily returned only 5 results, all of which were LinkedIn sub-paths blocked by HTTP 999. The entity had 0 GitHub links, 0 articles, and 0 tech blogs, causing enrichment to fail with `INSUFFICIENT_EVIDENCE`.

### 2. Root Cause Analysis
A single search query that couples a person's first name with their LinkedIn URL severely restricts search engine recall:
1. It biases results exclusively towards LinkedIn.
2. It fails to surface public GitHub repositories, Medium posts, Substack articles, conference talks, or company team pages where the individual's technical work is documented.

### 3. Reproducing Scenario & Production Logs
```
[Pipeline: DISCOVERY]
Searching Tavily with: '"Saloni" linkedin.com/in/saloni-sharma-872379180'
Tavily returned only 5 results.
[AI_EXTRACTION_SKIPPED] No non-blocked sources discovered. Status: INSUFFICIENT_EVIDENCE
```

### 4. Engineering Fix Applied
Implemented **Multi-Query Discovery Expansion** in `QueryBuilder` and `DefaultResearchDiscoveryService`:
Instead of 1 query, the service now generates up to 5 orthogonal queries:
1. **Identity Query**: `"${name}" "${company}"`
2. **Tech Stack & Engineering Query**: `"${name}" Java "Spring Boot" OR backend`
3. **Hiring & Leadership Query**: `"${name}" hiring OR recruiter OR "engineering manager"`
4. **Public Activity & Portfolio Query**: `"${name}" github.com OR dev.to OR medium.com`
5. **Exact Anchor Query**: `"${name}" "${canonicalDomain}"`

`DefaultResearchDiscoveryService` executes all queries concurrently, aggregates candidates into a unified pool, deduplicates them by normalized URL (`SourceDeduplicator`), and ranks them by authority and relevance (`SourceRanker`).

### 5. Verification & Regression Safeguards
- Verified with `MockSearchProvider`: produces GitHub repos, technical publications, and hiring posts for target entities (`saloni-sharma`, `monika`, `shyam`).
- All 151 unit tests pass in `research-service`.

### 6. System Lessons & Senior Engineering Takeaways
- **Query Diversity > Result Depth**: A single deep search query will almost always hit domain saturation. Generating orthogonal multi-aspect queries expands coverage across disparate technical communities.

---

## 7. `ResearchDepth` Enum Hardcoding & Contract Invariants

### 1. Summary & Error Signature
Attempting to increase discovery breadth by modifying the `ResearchDepth` enum:
```java
// ATTEMPTED CHANGE:
SHALLOW(5, 1), NORMAL(10, 2), DEEP(15, 3)
```
immediately broke existing regression tests:
```
RoadmapTasks16To25Test.shouldEnforceResearchDepthLimits: expected: <3> but was: <5>
```

### 2. Root Cause Analysis
The project had strict architectural regression tests validating Tasks 1 through 25, including `shouldEnforceResearchDepthLimits` which explicitly asserted the contract invariants:
- `SHALLOW.maxSources() == 3`
- `NORMAL.maxSources() == 5`
- `DEEP.maxSources() == 8`

Modifying the enum constants directly violated the backward-compatibility contract of the research API.

### 3. Reproducing Scenario & Production Logs
```
[ERROR] Failures: 
[ERROR]   RoadmapTasks16To25Test.shouldEnforceResearchDepthLimits:45 expected: <3> but was: <5>
```

### 4. Engineering Fix Applied
1. Preserved the immutable contract of `ResearchDepth`:
   ```java
   public enum ResearchDepth {
       SHALLOW(3, 0),
       NORMAL(5, 1),
       DEEP(8, 2);
   }
   ```
2. Decoupled search provider discovery accumulation from `ResearchDepth`:
   - Configured discovery query fetch limits in `DiscoveryProperties` (`maxResults = 10`).
   - Discovery fetches up to 10 candidates per query across 5 queries (up to 50 candidates).
   - Deduplicated and ranked candidates are filtered down to the exact `depth.maxSources()` limit only at the final crawling stage, satisfying both test contracts and rich multi-query discovery.

### 5. Verification & Regression Safeguards
- `RoadmapTasks16To25Test` passes 100% (all 25 tasks verified).
- `DefaultResearchDiscoveryServiceTest` passes with multi-query ranking.

### 6. System Lessons & Senior Engineering Takeaways
- **Contract Invariance**: Never alter established enum constants or public API contracts to satisfy internal implementation desires. Decouple pipeline discovery ceilings from domain limits.

---

## 8. SSE Pipeline Event Desynchronization & Thread Pool Starvation

### 1. Summary & Error Signature
When users ran large batch enrichment jobs (50+ rows), early rows completed, but later rows stayed stuck in `PROCESSING` indefinitely. Furthermore, if a browser tab was closed or refreshed, the backend worker threads hung.

### 2. Root Cause Analysis
1. `DefaultDatasetEnrichmentService` maintained active clients in a `List<SseEmitter>`. When a client disconnected abruptly, calling `emitter.send(...)` threw an `IOException` (Broken Pipe / Connection Reset).
2. If synchronous error handling did not immediately prune the dead emitter, subsequent events blocked waiting on TCP timeouts, starving the `enrichmentJobExecutor` thread pool.
3. Late-connecting clients had no event history replay and remained stuck on an empty progress bar.

### 3. Reproducing Scenario & Production Logs
```
java.io.IOException: An established connection was aborted by the software in your host machine
at org.apache.catalina.connector.OutputBuffer.realWriteBytes(OutputBuffer.java:348)
```

### 4. Engineering Fix Applied
1. Used thread-safe `ConcurrentHashMap` and `CopyOnWriteArrayList` for emitter registrations.
2. Wrapped every SSE event dispatch in an isolated try-catch block with automated cleanup:
   ```java
   List<SseEmitter> deadEmitters = new ArrayList<>();
   for (SseEmitter emitter : emitters) {
       try {
           emitter.send(SseEmitter.event().name(eventName).data(payload));
       } catch (Exception ex) {
           deadEmitters.add(emitter);
       }
   }
   emitters.removeAll(deadEmitters);
   ```
3. Added `jobEventHistory` buffer per active job: upon client connection, the server reinitializes state and replays history events in order before streaming live events.

### 5. Verification & Regression Safeguards
- Unit test `shouldIsolateRowFailureInBatch` verifies that row exceptions do not abort worker pool processing.
- Manual test with multiple reconnecting browser tabs confirms smooth event resumption.

### 6. System Lessons & Senior Engineering Takeaways
- **Push Pipelines Require Dead-Client Eviction**: Real-time event streams must treat client disconnections as standard operating procedure, never allowing a closed network socket to stall a background processing worker.

---

## 9. Hallucination vs Grounded Evidence Boundary (`NOT_FOUND` / `UNKNOWN` Discipline)

### 1. Summary & Error Signature
When evaluating an individual with limited public records, the AI model generated plausible-sounding descriptions:
`"Alex is a Senior Staff Engineer at Google with 10 years of experience in distributed Java systems."`
However, the discovered sources only contained a 1-sentence comment on a tech forum.

### 2. Root Cause Analysis
Foundation LLMs are trained to complete text and minimize perplexity; when prompted for structured fields without rigid anti-hallucination constraints, they extrapolate or fabricate attributes based on common industry patterns.

### 3. Reproducing Scenario & Production Logs
```
Target Field: currentRole
Extracted Value: "Lead Java Architect"
Evidence Snippet: ""
Source URL: null
Confidence: LOW
```
The model extracted a concrete role with zero grounded citation.

### 4. Engineering Fix Applied
1. Implemented strict system prompts in `GeminiAiExtractionService` and `DefaultProfileAssessmentService`:
   ```
   GROUNDING DIRECTIVE:
   Every single extracted attribute MUST be directly supported by an verbatim evidence quote from the provided text.
   If the evidence snippet does not explicitly state the fact, you MUST return "UNKNOWN" or "NOT_FOUND".
   DO NOT GUESS. DO NOT INFER. A value of "UNKNOWN" is considered a successful, high-quality answer.
   ```
2. Implemented programmatic validation: if an attribute is returned with value other than `"UNKNOWN"` but has an empty or null `evidenceSnippet`, the backend downgrades its confidence to `LOW` or overrides the value to `"UNKNOWN"`.

### 5. Verification & Regression Safeguards
- Unit test `AiExtractionControllerTest` verifies that sparse inputs return `"UNKNOWN"` for missing fields.
- Benchmark validation with the Java internship objective confirms no fabricated roles appear in enriched datasets.

### 6. System Lessons & Senior Engineering Takeaways
- **Honest Absence Beats Fabricated Presence**: In enterprise data enrichment, an `UNKNOWN` value is high-integrity data. A fabricated value is a liability that degrades downstream trust.

---

## 10. Database Schema Evolution & JSON Column Serialization for Deep Profiles

### 1. Summary & Error Signature
When upgrading `dataset-service` to persist rich profiles (dimensional scores, priority tiers, talking points, grounded findings), queries to save new attributes failed with:
```
java.sql.SQLException: Unknown column 'profile_json' in 'field list'
```

### 2. Root Cause Analysis
The relational schema `entities` only had columns for `id`, `display_name`, `entity_type`, `canonical_url`, and audit timestamps. Storing complex multi-dimensional object graphs (such as `ResearchProfile` and `ObjectiveAssessment`) required either a dozen normalized tables with cascading joins or structured JSON columns. Attempting to persist these objects before running database migrations caused runtime SQL exceptions.

### 3. Reproducing Scenario & Production Logs
```
org.springframework.dao.InvalidDataAccessResourceUsageException: JDBC exception executing SQL [INSERT INTO entities (id, canonical_url, display_name, entity_type, created_at, updated_at, profile_json) VALUES (?, ?, ?, ?, ?, ?, ?)] [Unknown column 'profile_json' in 'field list']
```

### 4. Engineering Fix Applied
1. Authored Flyway migration `V2__add_profile_and_assessment_columns.sql`:
   ```sql
   ALTER TABLE entities
       ADD COLUMN execution_status VARCHAR(64) DEFAULT 'COMPLETED',
       ADD COLUMN execution_message VARCHAR(512),
       ADD COLUMN priority_tier VARCHAR(32) DEFAULT 'NONE',
       ADD COLUMN relevance_score INT DEFAULT 0,
       ADD COLUMN profile_json LONGTEXT,
       ADD COLUMN assessment_json LONGTEXT,
       ADD COLUMN recommendation_json LONGTEXT,
       ADD COLUMN findings_json LONGTEXT;
   ```
2. Configured `ObjectMapper` with `JavaTimeModule` to serialize and deserialize rich profile graphs cleanly.
3. Updated `EnrichedEntity.java`, `PersistEntityRequest.java`, and `DefaultEntityPersistenceService.java` to map these columns with full backward compatibility for older rows.

### 5. Verification & Regression Safeguards
- Unit tests in `dataset-service` verify persistence and retrieval of `profile_json` and `assessment_json`.
- Application startup test `DatasetServiceApplicationTests.contextLoads()` verifies Flyway migration execution against test databases.

### 6. System Lessons & Senior Engineering Takeaways
- **Hybrid Relational/Document Storage**: Highly structured tabular data (IDs, URLs, status) belongs in indexed SQL columns; complex, rapidly-evolving analytical outputs (AI assessments, talk points) belong in structured JSON columns within the same entity row.

---

## 11. Docker Compose Microservice Gateway Resolution & Timeouts

### 1. Summary & Error Signature
When running under Docker Compose (`docker compose up`), `research-service` and `dataset-service` failed to connect to `ai-intelligent-service:9742`, logging:
```
java.net.ConnectException: Connection refused (Connection refused)
at java.net.http/jdk.internal.net.http.HttpClientImpl.send(HttpClientImpl.java:964)
```
and during large batch runs:
```
java.net.http.HttpTimeoutException: request timed out after 5000 MILLISECONDS
```

### 2. Root Cause Analysis
1. In `docker-compose.yml`, services communicate over a user-defined bridge network (`enrichment-network`). If environment variables in `application.yml` defaulted to `localhost` instead of container hostnames (`ai-intelligent-service:9742`, `research-service:9741`), cross-container requests failed immediately.
2. The default HTTP client timeout was set to 5 seconds. During peak load when Gemini performs multi-step analysis on multiple candidate sources, response latency can reach 8–15 seconds. A 5-second timeout caused premature connection aborts.

### 3. Reproducing Scenario & Production Logs
```
WARN  c.s.r.c.RestAiExtractionClient - AI extraction request to http://ai-intelligent-service:9742 failed: request timed out after 5000 MILLISECONDS
```

### 4. Engineering Fix Applied
1. Standardized environment variable defaults across all services:
   - `AI_INTELLIGENT_SERVICE_URL: http://ai-intelligent-service:9742`
   - `RESEARCH_SERVICE_URL: http://research-service:9741`
   - `DATASET_SERVICE_URL: http://dataset-service:9743`
2. Configured `RestClient.Builder` with generous, production-calibrated timeouts:
   ```java
   var factory = new SimpleClientHttpRequestFactory();
   factory.setConnectTimeout(Duration.ofSeconds(10));
   factory.setReadTimeout(Duration.ofSeconds(60)); // Accommodate LLM inference bursts
   ```
3. Added healthcheck dependencies in `docker-compose.yml` ensuring `mysql` and `ai-intelligent-service` are healthy before downstream consumers start processing traffic.

### 5. Verification & Regression Safeguards
- Verified end-to-end containerized communication over `enrichment-network`.
- Timeout settings verified in `ResearchServiceConfiguration.java` and `DatasetServiceConfiguration.java`.

### 6. System Lessons & Senior Engineering Takeaways
- **LLM Workflows Require Asymmetric Timeouts**: Standard microservice 2–5 second timeouts do not apply to distributed AI pipelines. Systems must configure connect timeouts strictly (5–10s) and read timeouts generously (60s), paired with worker pool rate limiting.
