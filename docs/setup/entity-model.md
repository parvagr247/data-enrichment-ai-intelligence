# Entity Model

> Status: Draft  
> Version: 0.1  
> Last Updated: 2026-09-04

## Purpose

This document outlines the initial domain and entity model for the Data Enrichment & Research Engine. It establishes the minimum required domain concepts to support the core workflow while maintaining a strict distinction between generic domain concepts and implementation details.

---

## 1. Core Workflow

The domain model supports a single fundamental data flow:

```text
Input (Seed URL / Sparse Data)
         │
         ▼
       Entity
         │
         ▼
      Research
         │
         ▼
      Evidence
         │
         ▼
     Enrichment
         │
         ▼
 Structured Output
```

1. **Input**: A seed URL, raw record, or identifier provided to the engine.
2. **Entity**: The logical subject represented by the seed.
3. **Research**: The multi-source discovery and retrieval process.
4. **Evidence**: Verbatim, sourced factual snippets gathered during research.
5. **Enrichment**: AI reasoning and normalization synthesizing evidence into schema fields.
6. **Structured Output**: A verified record with field-level provenance and confidence ratings.

---

## 2. Core Domain Concepts

### 2.1 Entity

The **Entity** represents the logical subject being enriched. It is domain-agnostic and serves as the anchor for all discovered evidence and enrichment attributes.

#### Information Carried by an Entity:
* **id**: Deterministic canonical identifier (e.g., SHA-256 hash derived from the canonical URL or primary identifier).
* **type**: Generic classification category (`PERSON`, `ORGANIZATION`, `PRODUCT`, `REPOSITORY`, `WEBSITE`, `OTHER`).
* **name**: Primary display name or label (as known from input or initial resolution).
* **inputUrls**: One or more seed URLs associated with the entity (e.g., personal homepage, profile URL, company domain).
* **existingAttributes**: Sparse key-value attributes provided at ingestion (e.g., raw role, company name, location).
* **status**: Lifecycle state of the entity (`PENDING`, `RESEARCHING`, `ENRICHED`, `FAILED`).
* **createdAt / updatedAt**: Timestamps for lifecycle tracking.

#### Generic Modeling Principle:
The entity model is generic and is not hard-coded to any single domain or platform (such as LinkedIn). It models people, companies, organizations, open-source repositories, products, and websites uniformly without requiring distinct database entities or class hierarchies at this stage.

---

### 2.2 Research Request / Job

A **Research Request** represents an individual unit of research execution.

#### Information Carried by a Research Request:
* **targetEntity**: Reference to the entity or seed identifier being researched.
* **seedUrl**: Primary URL driving initial discovery.
* **status**: Execution state (`SUBMITTED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`).
* **startedAt / completedAt**: Execution timestamps.
* **errorInfo**: Error code and descriptive message if research fails (e.g., unresolvable URL, blocked domain).

#### Operational Role:
In the initial single-record synchronous workflow, a research request exists ephemerally as an in-memory execution context or request DTO. It does not require a persistent database record until asynchronous, queued, or batch workloads are introduced.

---

### 2.3 Evidence

**Evidence** is a first-class domain concept representing empirical data retrieved from public and permitted sources. It is the mandatory bridge between raw web content and AI-generated assertions.

#### Information Carried by Evidence:
* **source**: Identifier or type of the information provider (e.g., `DIRECT_PAGE`, `SEARCH_ENGINE`, `PUBLIC_API`).
* **url**: Canonical URL from which the data was retrieved.
* **retrievedContent**: Raw or cleaned textual excerpt extracted verbatim from the source.
* **retrievedAt**: ISO-8601 timestamp of retrieval.
* **confidence**: Source-level reliability rating based on domain authority and accessibility.

#### Operational Role:
Evidence guarantees zero-hallucination compliance. An entity attribute cannot be asserted unless it is directly grounded in one or more evidence items.

---

### 2.4 Enrichment Result

The **Enrichment Result** represents the structured, verified data asset produced after autonomous research and Spring AI extraction.

#### Information Carried by an Enrichment Result:
* **entityId**: Reference to the canonical entity.
* **attributes**: Map of structured domain attributes. Each attribute is an **Evidence Tuple**:
  * `value`: Extracted value (string, list, or number).
  * `sourceUrl`: URL of the source providing the factual backing.
  * `evidenceSnippet`: Verbatim quotation substantiating the value.
  * `confidence`: Field-level confidence tier (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`).
* **provenance**: Metadata detailing the enrichment run:
  * `enrichedAt`: Timestamp of completion.
  * `toolsInvoked`: List of search/fetch tools called during research.
  * `totalSourcesConsulted`: Number of external sources evaluated.

If an attribute cannot be corroborated from retrieved evidence, its value must be set to `UNKNOWN` with `null` evidence.

---

## 3. Domain Concepts vs. Implementation Details

A domain concept is not synonymous with a database table or JPA entity. Premature mapping of domain concepts to persistence schemas creates unnecessary complexity before workflows are validated.

| Domain Concept | In-Memory / Current Representation | Persistent / Future Representation |
| :--- | :--- | :--- |
| **Entity** | In-memory Java record / DTO (`GenericEntity`) | Relational database table (`entities`) via Spring Data JPA |
| **Research Request / Job** | Method call parameter / Request DTO (`ResearchRequest`) | Job queue record (`research_jobs`) for async batch tracking |
| **Evidence** | Transient collection of records (`EvidenceItem`) | Evidence audit table or JSONB log if auditability is required |
| **Enrichment Result** | Response DTO (`EnrichmentResult`) | Relational / Document table (`enrichment_results`) |

> **Rule**: Do not introduce a database, JPA annotations, or repositories simply because a concept exists in the domain model. In-memory data structures are sufficient for validating initial research flows.

---

## 4. Current vs. Planned vs. Not Yet Required

### CURRENT (What we are implementing now)
* Generic, domain-agnostic entity representation using in-memory Java records/POJOs.
* In-memory Evidence structures passed directly between research tools and Spring AI extraction components.
* Single-request `ResearchRequest` and `EnrichmentResult` models representing synchronous execution.
* Zero persistence overhead (no database, no ORM, no migrations).

### PLANNED (What we may implement later)
* Relational persistence (PostgreSQL via Spring Data JPA) for `Entity` and `EnrichmentResult` once session-spanning persistence is required.
* Persistent `ResearchJob` entity to track asynchronous, multi-minute, or high-volume batch runs.
* Entity change auditing to record how attributes evolve across successive enrichment cycles.

### NOT YET REQUIRED (Deliberately excluded)
* Specialized entity subclasses or separate database tables for `Person`, `Company`, `Product`, etc.
* Graph database representations or ontological knowledge graphs.
* Complex distributed caching or distributed entity locks.
