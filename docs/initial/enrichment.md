# Enrichment & Research Engine

> **Status:** Current  
> **Version:** 0.1  
> **Last Updated:** 2026-09-04

## Purpose

This document details the core enrichment workflow, autonomous research engine, Spring AI tool-calling design, evidence-and-provenance model, and empirical research limitations.

---

## 1. Enrichment Workflow

Enrichment transforms low-information entity seeds into structured, evidence-backed profiles:

```text
1. Input Record ──► Seed URL, Name, Sparse Attributes
          │
          ▼
2. Research & Discovery ──► Inspect Entry Point, Query Public Search Engines
          │
          ▼
3. Retrieval & Evidence ──► Fetch Accessible Pages, Query Public APIs, Store Raw Snippets
          │
          ▼
4. AI Information Extraction ──► Spring AI Model Maps Snippets to Schema Fields
          │
          ▼
5. Normalization & Pruning ──► Standardize Titles/Companies, Discard Conflicting Data
          │
          ▼
6. Structured Output ──► Emit Entity JSON with Field-Level Provenance & Confidence
```

---

## 2. Research Engine & URL Research

### Core Principle: Search First, Reason Second, Structure Third

An LLM cannot see what lies behind a URL without active tools. A URL is an **entry point for research**, not pre-loaded knowledge. 

The Research Engine treats the input URL and basic identifiers as clues to explore public and permitted sources, assembling corroborating evidence before triggering AI extraction.

### Multi-Step Execution Strategy

```text
Input URL & Identifiers
            │
            ▼
[1. Entity Identification]
Extract Name, Affiliation, Seed Keywords
            │
            ▼
[2. Multi-Source Discovery]
Inspect Primary URL ──► Query Search Engines ──► Search Public Registries
            │
            ▼
[3. Content Retrieval]
Fetch Accessible HTML / API Payloads (Polite HTTP Client)
            │
            ▼
[4. Evidence Compilation]
Assemble Verifiable Text Snippets with Source URLs
            │
            ▼
[5. AI Extraction]
Map Verified Facts to Standard Schema Fields
            │
            ▼
[6. Validation & Pruning]
Discard Inconsistent Snippets; Mark Gaps as UNKNOWN
            │
            ▼
[7. Structured Result]
Emit Field Values + Confidence Ratings + Direct Citations
```

### Permitted Sources

* **Direct Public Pages**: Openly accessible over standard HTTP/HTTPS without authentication.
* **Public Search Engines**: Authorized search APIs (Google Custom Search, Bing, Tavily) to locate related articles and author pages.
* **Developer Platforms**: GitHub, GitLab, personal portfolios, technical publications.
* **Corporate & Academic Registries**: Official corporate websites, university directories, open research repositories.
* **Permitted Public APIs**: Any legitimate REST/GraphQL endpoint providing structured public records.

---

## 3. Tool Calling (Spring AI)

The enrichment layer leverages **Spring AI's function-calling mechanism** to equip a single research agent with discrete, deterministic external tools.

### Architecture

```text
+-------------------------------------------------------------+
|                     Research Agent                          |
|  (Spring AI ChatModel - Prompt: "Collect evidence for X")  |
+-------------------------------------------------------------+
                               │
                +--------------+--------------+
                │                             │
                ▼                             ▼
      +-------------------+         +-------------------+
      |    Tool: fetchUrl |         |  Tool: searchWeb  |
      | (Polite HTTP/DOM) |         | (Search API/SERP) |
      +-------------------+         +-------------------+
                │                             │
                ▼                             ▼
      +-------------------+         +-------------------+
      | Tool: searchGitHub|         |Tool: lookupCompany|
      | (Public Repo API) |         | (Domain Registry) |
      +-------------------+         +-------------------+
```

### Planned Tool Contracts

```java
// PLANNED INTERFACE — NOT YET IMPLEMENTED IN APPLICATION CODE
public interface EnrichmentTools {

    @Description("Fetches public text content from a specified URL, stripping HTML markup.")
    FetchResult fetchUrl(FetchUrlRequest request);

    @Description("Searches public web sources using search queries to find relevant profile/entity pages.")
    SearchResult searchWeb(SearchWebRequest request);

    @Description("Searches GitHub for public profiles, repositories, and technical contributions.")
    GitHubResult searchGitHub(GitHubSearchRequest request);

    @Description("Looks up official company domain, industry, and organizational details.")
    CompanyResult lookupCompany(CompanyLookupRequest request);
}
```

### Single-Agent Focus

* Begin with **one Research Agent**.
* Avoid multi-agent swarms, complex orchestrators, or inter-agent debate until single-agent execution is proven and insufficient.

---

## 4. Evidence, Provenance & Confidence

### Evidence Tuple Pattern

Every extracted attribute is paired with its empirical citation:

```yaml
attribute_name:
  value: "<Extracted Value>"
  source_url: "<Canonical URL Where Evidence Was Found>"
  evidence_snippet: "<Exact verbatim text quotation backing the value>"
  confidence: "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN"
```

### The Zero-Hallucination Policy

If an attribute cannot be found in retrieved text snippets:

```yaml
attribute_name:
  value: "UNKNOWN"
  source_url: null
  evidence_snippet: null
  confidence: "UNKNOWN"
```

Under no circumstances should the LLM invent plausible backgrounds, dates, or skills.

### Confidence Tier Classification

| Tier | Definition & Criteria |
| :--- | :--- |
| **HIGH** | Verbatim match from primary official source (e.g., official domain, verified profile) or corroborated by 2+ independent public citations. |
| **MEDIUM** | Inferred from secondary public web content, reputable news articles, or public conference listings. |
| **LOW** | Single indirect mention with potential ambiguity or conflicting naming. |
| **UNKNOWN** | Evidence is absent or insufficient; field explicitly designated as unknown. |

### Generic Enriched Entity Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "GenericEnrichedEntity",
  "type": "object",
  "properties": {
    "entity_id": { "type": "string", "description": "Deterministic SHA-256 hash" },
    "entity_type": { "type": "string", "enum": ["PERSON", "ORGANIZATION", "PRODUCT", "REPOSITORY"] },
    "canonical_url": { "type": "string" },
    "display_name": { "type": "string" },
    "attributes": {
      "type": "object",
      "additionalProperties": {
        "type": "object",
        "properties": {
          "value": { "type": ["string", "number", "boolean", "array"] },
          "source_url": { "type": ["string", "null"] },
          "evidence_snippet": { "type": ["string", "null"] },
          "confidence": { "type": "string", "enum": ["HIGH", "MEDIUM", "LOW", "UNKNOWN"] }
        },
        "required": ["value", "confidence"]
      }
    },
    "provenance": {
      "type": "object",
      "properties": {
        "enriched_at": { "type": "string", "format": "date-time" },
        "tools_invoked": { "type": "array", "items": { "type": "string" } },
        "total_sources_consulted": { "type": "integer" }
      },
      "required": ["enriched_at", "tools_invoked"]
    }
  },
  "required": ["entity_id", "entity_type", "display_name", "attributes", "provenance"]
}
```

---

## 5. Research Limitations & Empirical Findings

### Known Limitations

1. **Restricted & Authenticated Pages**: Content behind login walls, session cookies, or paywalls cannot be scraped. The engine must fall back to public search or return `UNKNOWN`.
2. **Client-Rendered Dynamic Pages**: Standard HTTP clients do not execute heavy client-side JavaScript. Headless browsers introduce high latency/memory and are avoided in initial phases.
3. **Search Indexing Lags**: Search APIs reflect indexed snapshots, not instant real-time changes.
4. **Rate Limits & Quotas**: Upstream search APIs and domains enforce QPS limits. Requests must be paced, queued, and cached.
5. **Partial Public Information**: Most entities do not have complete web footprints. The platform measures success by:
$$\text{Requested Fields} \longrightarrow \text{Retrieved Fields} \longrightarrow \text{Verified Fields} \longrightarrow \text{Confidence}$$
6. **Disambiguation Challenges**: Common names require secondary context (company, domain, location) to prevent incorrect attribution.
7. **Bot Protections**: Cloudflare, CAPTCHAs, and IP reputation filters block automated requests; such URLs are logged and skipped.

---

## Current State & Planned Work

* **Current**: Research engine workflow, Spring AI tool contracts, evidence tuple schema, and limitations formally documented.
* **Planned**: Implement the single-entity research flow in `apps/backend/` using Spring AI function calling.
