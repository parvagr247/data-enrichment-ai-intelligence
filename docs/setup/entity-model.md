# Entity Model

> Status: Active  
> Version: 1.0  
> Last Updated: 2026-09-05

## Purpose

This document outlines the authoritative domain and entity model for the Data Enrichment & Research Engine. It details generic domain concepts, evidence tuples, multi-source corroboration, conflict detection, and the relational persistence schema in `dataset-service`.

---

## 1. Core Workflow & Domain Life Cycle

```text
Input (Seed URL / Sparse Data)
         │
         ▼
       Entity (Deterministic SHA-256 ID, Canonical URL)
         │
         ▼
      Research (Discovery via Tavily/Mock, Scraped Sources)
         │
         ▼
      Evidence (Exact snippets, citations, retrieval timestamps)
         │
         ▼
  Multi-Source Corroboration Engine (Agreement detection, Confidence boosting)
         │
         ▼
     Enrichment (Structured attributes with corroborating sources)
         │
         ▼
  Relational Persistence (`dataset-service` MySQL: Entities, Sources, Attributes)
```

1. **Input**: A seed URL, raw record, or identifier provided to the engine.
2. **Entity**: The logical subject represented by the seed, normalized with tracking parameters stripped and query parameters sorted.
3. **Research**: Multi-source discovery and polite retrieval bounded by configurable guardrails.
4. **Evidence**: Verbatim, sourced factual snippets gathered during research.
5. **Multi-Source Corroboration**: Comparing multiple sources for agreement; upgrading confidence from `LOW` &rarr; `MEDIUM` or `MEDIUM` &rarr; `HIGH`, tracking corroborating URLs, and flagging attribute conflicts.
6. **Enrichment**: Structured attribute graph mapped to confidence tiers.
7. **Relational Persistence**: Master snapshot stored in MySQL with indexed query access and pagination.

---

## 2. Core Domain Concepts

### 2.1 Entity

The **Entity** represents the logical subject being enriched. It is domain-agnostic and serves as the anchor for all discovered evidence and enrichment attributes.

#### Information Carried by an Entity:
* **id**: Deterministic canonical identifier (SHA-256 hash derived from the canonical URL).
* **type**: Generic classification category (`PERSON`, `ORGANIZATION`, `PRODUCT`, `REPOSITORY`, `WEBSITE`, `OTHER`).
* **displayName**: Primary display name or label.
* **canonicalUrl**: Fully normalized canonical URL (tracking query params removed, functional params sorted).
* **createdAt / updatedAt**: Timestamps for lifecycle tracking.

### 2.2 Evidence & The Evidence Tuple

**Evidence** guarantees zero-hallucination compliance. An entity attribute cannot be asserted unless it is directly grounded in one or more evidence items.

#### Attributes as Evidence Tuples:
Each attribute in the enriched record is represented as an `EvidenceTuple`:
* **value**: Extracted fact string (or `UNKNOWN` if not grounded).
* **sourceUrl**: Primary source URL providing factual backing.
* **evidenceSnippet**: Exact verbatim quotation substantiating the value.
* **confidence**: Field-level confidence tier (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`).
* **corroboratingSources**: List of additional URLs corroborating the fact.
* **conflictDetected**: Boolean indicating if conflicting assertions were discovered across sources.

---

## 3. Relational Persistence Schema (`dataset-service`)

The `dataset-service` persists enriched entities into a normalized MySQL relational schema:

### 3.1 Tables & Schema

```sql
-- Master entity records
CREATE TABLE enriched_entities (
    entity_id VARCHAR(64) PRIMARY KEY,
    canonical_url VARCHAR(2048) NOT NULL,
    display_name VARCHAR(255),
    entity_type VARCHAR(64) NOT NULL DEFAULT 'OTHER',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_entities_type (entity_type),
    INDEX idx_entities_updated (updated_at)
);

-- Retrieved sources and provenance
CREATE TABLE enriched_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    retrieved_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_sources_entity FOREIGN KEY (entity_id) REFERENCES enriched_entities(entity_id) ON DELETE CASCADE,
    INDEX idx_sources_entity_id (entity_id)
);

-- Corroborated attributes and evidence
CREATE TABLE enriched_attributes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    attribute_name VARCHAR(128) NOT NULL,
    attribute_value TEXT,
    source_url VARCHAR(2048),
    evidence_snippet TEXT,
    confidence_tier VARCHAR(32) NOT NULL,
    CONSTRAINT fk_attributes_entity FOREIGN KEY (entity_id) REFERENCES enriched_entities(entity_id) ON DELETE CASCADE,
    INDEX idx_attributes_entity_id (entity_id),
    INDEX idx_attributes_name (attribute_name)
);
```

### 3.2 Pagination & Retrieval
- `GET /api/v1/entities?page=0&size=20`: Queries `enriched_entities` sorted by `updated_at DESC`.
- `GET /api/v1/entities/{entityId}`: Joins entity with `enriched_sources` and `enriched_attributes` to return the complete evidence graph.
