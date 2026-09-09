# Domain Glossary & Terminology

This glossary defines core terminology, algorithmic concepts, and architectural patterns utilized across the Data Enrichment AI Intelligence Platform.

---

### Anti-Spoofing Architecture
An ingress security design implemented in `api-gateway` where all client-supplied identity headers (such as `X-User-Id`, `X-User-Email`, and `X-User-Roles`) are unconditionally stripped by a request wrapper. After verifying the signature and expiration of the client's JWT Bearer token, the Gateway injects verified `X-User-Id` and `X-User-Email` headers downstream.

---

### Bounded Concurrency
A concurrency design pattern implemented via `BoundedExecutorService` that constrains parallel row processing to a fixed thread pool (default: 3 workers) with a bounded queue and caller-runs / rejection policies. This prevents thread starvation, high CPU churn, and third-party API rate-limiting (HTTP 429).

---

### Confidence Tier
A discrete classification (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`) assigned to an enriched attribute based on its provenance:
* **`HIGH`**: Corroborated across multiple independent sources or anchored by an exact verbatim quote from an authoritative primary domain.
* **`MEDIUM`**: Sourced from a single reputable secondary source or verified search snippet.
* **`LOW`**: Inferred from contextual heuristics or uncorroborated third-party snippets.
* **`UNKNOWN`**: Attribute was requested but no verifiable source evidence was discovered.

---

### Corroboration
The process of independently confirming a factual attribute across two or more distinct web domains. Corroboration increases the attribute's confidence score and detects conflicting information (e.g., mismatched headquarters or conflicting executive titles).

---

### Delimiter Sniffing
An automated heuristic implemented in `DelimiterSniffer` (`dataset-service`) that inspects the first several lines of an uploaded text file to identify its delimiter (comma `,`, semicolon `;`, tab `\t`, or pipe `|`), eliminating manual CSV format configuration.

---

### Deterministic Fallback
An architectural resilience pattern where high-level operations (such as Spring AI LLM extraction or web crawling) transparently fall back to deterministic regex, heuristic parsing, or mock providers upon external API quota exhaustion, network timeouts, or rate limits.

---

### Evidence Tuple
The immutable factual triad `(value, exactQuote, sourceUrl)` backing every enriched attribute. No fact is admitted into the platform database without this grounding proof.

---

### IDOR Protection (Insecure Direct Object Reference)
A multi-tenant data boundary pattern where all batch jobs (`JobState`) and persisted entities are bound to the owner's `userId`. Requests to read, cancel, or modify a job owned by another user are rejected with `404 Not Found` or `403 Forbidden`.

---

### Malformed Row Isolation
An ingestion safeguard implemented in `MalformedRowIsolator` (`dataset-service`) that detects and quarantines rows with ragged column counts, unclosed quotes, or encoding corruption into an isolated error report without failing the batch processing of valid rows.

---

### Non-Destructive Export
The guarantee that dataset export operations (CSV / XLSX) preserve all original user columns, ordering, and formatting unaltered, appending newly discovered attributes with an `Enriched_` prefix alongside `Canonical_Url` and `Enrichment_Status`.

---

### Problem Details (RFC 7807)
The IETF standard specification for HTTP API error responses. Formatted as JSON with fields: `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId`, and `timestamp`.

---

### Seed Cleansing
The pre-processing pipeline in `ai-intelligent-service` that normalizes raw entity input before research begins: stripping corporate suffixes (`Inc.`, `LLC`, `GmbH`), removing professional honorifics (`Dr.`, `Ph.D.`), stripping URL tracking parameters (`utm_source`), and resolving canonical domain names.

---

### SSRF Protection (Server-Side Request Forgery)
A defensive validation guard implemented in `SecurityValidator` (`research-service`) that checks all target URLs before dispatching HTTP web crawlers. It blocks attempts to connect to `localhost`, loopback addresses (`127.0.0.1`, `::1`), RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), and cloud instance metadata endpoints (`169.254.169.254`).

---

### Verbatim Evidence Grounding (Zero-Hallucination Principle)
The invariant enforced by `ai-intelligent-service` requiring every LLM-extracted claim to be accompanied by a verbatim snippet (`exactQuote`). The service verifies programmatically that `sourceText.contains(exactQuote)`. If the quote cannot be found verbatim in the crawled text, the fact is rejected.
