# Concept 03: Service Abstraction and SOLID Principles in Practice

To avoid messy monolithic classes (such as 500-line "God services"), our services adhere to pragmatic SOLID principles that directly improve code readability, testability, and maintainability.

---

## 1. Single Responsibility Principle (SRP)

Instead of a single monolithic orchestration method executing normalization, querying, HTTP calls, parsing, resolution, and persistence sequentially, each responsibility is isolated into a focused class or method:

| Class | Single Responsibility |
| :--- | :--- |
| `ResearchRequestValidator` | Validates target presence and well-formed HTTP/HTTPS URL formats. |
| `DefaultEntityNormalizer` | Normalizes URLs, generates entity slugs and deterministic URNs / SHA-256 IDs. |
| `QueryBuilder` | Generates search provider queries tailored to entity type and domain hints. |
| `DefaultResearchDiscoveryService` | Coordinates provider invocation, logging, and error categorization. |
| `SourceProcessor` | Deduplicates candidate URLs, strips tracking parameters (`utm_*`), and classifies sources. |
| `WebContentFetcher` | Executes HTTP requests against discovered source URLs with timeouts and size limits. |
| `ContentExtractor` | Cleans raw HTML/text into readable markdown/plain text up to configured maximum size. |
| `EntityResolver` | Verifies whether fetched source content genuinely references the target entity. |
| `EvidenceExtractor` | Extracts verified facts with provenance citations and confidence tiers. |
| `SourceEvidenceService` | Coordinates content retrieval, extraction, resolution, and evidence aggregation. |
| `ResearchSnapshotPersister` | Resiliently persists entity snapshots to `dataset-service` without failing research flow. |
| `ResearchPipeline` | Orchestrates the sequential pipeline stages via a lightweight `ResearchContext`. |
| `ResearchResponseFactory` | Maps domain models to DTOs, builds execution metadata, and computes status. |
| `DefaultResearchService` | Thin use-case facade delegating to the research pipeline. |

---

## 2. Open/Closed Principle (OCP)

The pipeline is open for extension but closed for modification:
- Adding a new search engine (e.g., Google Custom Search, Bing, Brave Search) requires only implementing the `SearchProvider` interface and registering a bean—no changes to `DefaultResearchService` or pipeline controllers.
- Adding a new AI model (e.g., Anthropic Claude, OpenAI, local Ollama) requires only adding an implementation of `ExtractionService` in `ai-intelligent-service`.

---

## 3. Liskov Substitution Principle (LSP)

Implementations of `SearchProvider` (`MockSearchProvider`, `TavilySearchProvider`) honor the same behavioral contract:
- If results are found, return `List<DiscoveredSource>` ordered by initial provider relevance.
- If no results exist, return an empty list `List.of()`, never `null`.
- On provider network or timeout failure, throw `ExternalServiceException` with root cause attached.

---

## 4. Interface Segregation Principle (ISP)

Clients depend only on what they consume:
- `ResearchService` exposes synchronous execution (`executeResearch`).
- `ResearchJobService` exposes asynchronous submission and polling (`submitJob`, `getJob`).
- Controllers and test classes do not need to know about job storage internals or thread pool state.

---

## 5. Dependency Inversion Principle (DIP)

High-level modules do not depend on low-level modules. Both depend on abstractions:
- `DefaultResearchService` depends on `SearchProvider`, not on `TavilySearchProvider`.
- `InMemoryResearchJobService` depends on `ResearchService`, not `DefaultResearchService`.
