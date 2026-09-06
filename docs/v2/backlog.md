# V2 Prioritized Engineering Task Backlog

> [!WARNING]
> **PROPOSED SPECIFICATION / NOT IMPLEMENTED YET**  
> Baseline: `v1.0.0` frozen release.  
> **Priorities**: **P0** = Required for Core V2 | **P1** = High Value / Stability | **P2** = Advanced / Nice-to-have

---

## Task Backlog Matrix (36 Targeted Engineering Tasks)

| Task ID | Task Name | Priority | Affected Component | Dependencies | Description |
| :--- | :--- | :---: | :--- | :--- | :--- |
| **V2-01** | Decouple `RowEnrichmentResult` | **P0** | `dataset-service` | None | Split monolithic row result into discrete `InputRecord`, `EntityIdentity`, `EvidenceCitation`, and `AttributeAssertion`. |
| **V2-02** | Flyway V3 Job Task Schema | **P0** | `dataset-service` | None | Create `enrichment_jobs` and `enrichment_job_tasks` tables in MySQL with indexes on status. |
| **V2-03** | Relational Task State Machine | **P0** | `dataset-service` | V2-02 | Replace in-memory `JobState` map with database repository managing row lifecycle. |
| **V2-04** | Job Control Plane (`/pause`, `/resume`) | **P0** | `dataset-service` | V2-03 | Implement graceful pause and resume endpoints for active batch enrichment jobs. |
| **V2-05** | Job Cancellation Endpoint | **P0** | `dataset-service` | V2-03 | Implement `/api/v1/enrichment/jobs/{id}/cancel` to halt unstarted row tasks. |
| **V2-06** | Row Retry with Exponential Jitter | **P0** | `dataset-service` | V2-03 | Automatically retry transient network/scraping row failures up to 2 times before marking `FAILED`. |
| **V2-07** | Unified MDC Tracing Filter | **P0** | All Services | None | Propagate `X-Correlation-ID`, `X-Job-ID`, and `X-Row-ID` across RestTemplate clients and logging. |
| **V2-08** | RFC 7807 Problem Detail Handlers | **P0** | All Services | None | Standardize `@ExceptionHandler` responses returning `application/problem+json` with typed error URIs. |
| **V2-09** | `EnrichmentProfile` Domain Model | **P0** | `dataset-service`, `ai-service` | V2-01 | Implement profile entity representing structured target fields, data types, and search keywords. |
| **V2-10** | Profile CRUD REST Endpoints | **P0** | `dataset-service` | V2-09 | Implement `GET /api/v1/profiles` and `POST /api/v1/profiles` for saving reusable enrichment schemas. |
| **V2-11** | Requirement-Weighted Query Formulator | **P0** | `research-service` | V2-09 | Formulate search queries prioritizing entity identity combined with profile-defined target keywords. |
| **V2-12** | Search Provider Circuit Breaker | **P0** | `research-service` | None | Wrap Tavily and external search calls in a rate-limit bucket with automatic fallback to Mock provider. |
| **V2-13** | Paragraph Scoring & Token Budgeter | **P0** | `research-service`, `ai-service` | V2-11 | Score scraped paragraphs and only send top relevant text to Spring AI, reducing token usage by ~50%. |
| **V2-14** | Verbatim Quote Substring Validator | **P0** | `ai-service` | None | Deterministically verify that every extracted fact quote exists as an exact substring in scraped text. |
| **V2-15** | Token Cost & Latency Metrics | **P0** | `ai-service` | None | Capture `promptTokens`, `completionTokens`, and inference latency on all Spring AI responses. |
| **V2-16** | Paginated Batch Job Results | **P0** | `dataset-service` | V2-03 | Add `?page=0&size=50` pagination to `GET /api/v1/enrichment/jobs/{jobId}`. |
| **V2-17** | Frontend Job Control UI | **P0** | `apps/frontend` | V2-04, V2-05 | Add Pause, Resume, and Cancel buttons to the active enrichment progress view. |
| **V2-18** | Frontend Profile Selector & Editor | **P0** | `apps/frontend` | V2-10 | Allow users to choose existing profiles or define custom fields in the column confirmation stage. |
| **V2-19** | SSRF Outbound URL Validator | **P0** | `research-service` | None | Block outbound scraping to private, loopback, and cloud metadata IP ranges (`169.254.169.254`). |
| **V2-20** | Spreadsheet Cell Formula Sanitizer | **P0** | `apps/frontend`, `dataset-service` | None | Strip leading `=`, `+`, `-`, `@` characters from uploaded CSV/XLSX cells to prevent CSV injection. |
| **V2-21** | Crash-Resilient Job Recovery | **P1** | `dataset-service` | V2-03 | On startup, detect orphaned `RUNNING` tasks without active worker heartbeats and reset to `PENDING`. |
| **V2-22** | Domain Authority Scoring Engine | **P1** | `research-service` | None | Rank official domains, GitHub, and verified directories higher than syndicators and content scrapers. |
| **V2-23** | Temporal Corroboration Engine | **P1** | `research-service` | None | Differentiate between current and historical facts (e.g. past employer vs current employer) using dates. |
| **V2-24** | Cross-Row Entity Deduplication | **P1** | `dataset-service` | V2-01 | Detect duplicate entities within an uploaded spreadsheet and share research execution across identical rows. |
| **V2-25** | Spring Boot Actuator Prometheus Metrics | **P1** | All Services | None | Expose `/actuator/prometheus` with custom timers for batch duration, scrape latency, and AI costs. |
| **V2-26** | Frontend Virtualized Table | **P1** | `apps/frontend` | V2-16 | Use virtual scrolling to render datasets with 1,000+ rows smoothly without DOM stutter. |
| **V2-27** | Frontend Status & Confidence Filters | **P1** | `apps/frontend` | None | Allow filtering results by `COMPLETED` / `PARTIAL` / `FAILED` and confidence tiers (`HIGH` / `MEDIUM` / `LOW`). |
| **V2-28** | Batch Completion Webhook Dispatcher | **P1** | `dataset-service` | V2-03 | Post job summary payload to user-configured `webhookUrl` upon batch completion. |
| **V2-29** | Ollama Local Model Adapter | **P1** | `ai-service` | None | Implement local LLM execution via Spring AI `OllamaChatModel` for offline/air-gapped deployments. |
| **V2-30** | Granular Evidence Retrieval API | **P1** | `research-service` | None | Implement `GET /api/v1/research/evidence/{id}` to inspect full raw HTML and offset metadata. |
| **V2-31** | Disambiguation Suggestion Prompts | **P2** | `ai-service` | None | When entity matches multiple identities, generate suggested clarifying questions for user selection. |
| **V2-32** | Interactive Evidence DOM Snippet Viewer | **P2** | `apps/frontend` | V2-30 | Render highlighted HTML snippet directly inside the frontend evidence inspection modal. |
| **V2-33** | Dynamic Concurrency Tuning | **P2** | `dataset-service` | None | Dynamically adjust thread pool concurrency based on search provider latency and rate limits. |
| **V2-34** | Custom Regex Field Validation | **P2** | `ai-service` | V2-09 | Enforce user-defined regex rules (e.g. email, phone, ISO date) on extracted attribute values. |
| **V2-35** | PDF & Document Scraper Adapter | **P2** | `research-service` | None | Add Apache Tika/PDFBox parser to extract evidence from linked PDF whitepapers and corporate reports. |
| **V2-36** | Automated Regression Performance Suite | **P2** | Testing | None | Benchmark batch latency and token consumption against `v1-regression-dataset.csv` in CI pipeline. |
