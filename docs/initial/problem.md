# Problem Statement & Scope

> **Status:** Current  
> **Version:** 0.1  
> **Last Updated:** 2026-09-04

## Purpose

This document defines the core data-enrichment challenge, why existing datasets are insufficient, the project's objectives and scope, and the essential real-world constraints governing our solution.

---

## 1. The Problem

Organizations, researchers, and professionals frequently handle tabular datasets containing minimal surface-level records:

```text
Name / Entity Identifier
URL (website, public profile, repository, or article)
Basic Attributes (stated role, organization, or category)
```

### Why the Problem Exists

1. **Surface-Level Context**: Raw input exports lack depth—historical background, detailed technical skills, organizational scale, verified credentials, and recent contributions are missing.
2. **Manual Research Bottleneck**: Manually researching hundreds or thousands of entities (e.g., a dataset of ~2,500 records) across the web is labor-intensive, slow, inconsistent, and commercially unscalable.
3. **Keyword Filtering Fails**: Simple string filters cannot evaluate qualitative relevance, understand semantic context, or synthesize facts scattered across multiple public sources.
4. **Data Staleness**: Static exports quickly become obsolete as titles, organizations, and public activities evolve.

### Generic Nature of the Problem

The problem is **domain-agnostic and source-agnostic**. 

While professional connections (e.g., LinkedIn connection exports) serve as an initial benchmark dataset for development, the system is fundamentally designed for **any identifiable entity** backed by a URL or stable identifier:
* **People / Professionals**: Role history, technical expertise, publications, public code contributions.
* **Organizations / Companies**: Industry, size, product lines, tech stack, official domains.
* **Open-Source Repositories**: Primary language, maintainers, activity trends, dependencies.
* **Products & Services**: Category, features, pricing models, vendor organization.
* **Academic Publications**: Authors, citations, research domains, abstract summaries.

---

## 2. Project Objective

The objective is to build an automated, generic data-enrichment and AI intelligence platform that transforms sparse entity records into rich, verified, structured, and actionable data assets.

```text
Sparse Input Dataset (CSV / JSON)
               │
               ▼
   Autonomous Web Research
               │
               ▼
   Corroborated Evidence
               │
               ▼
   Structured Normalization
               │
               ▼
   Configurable AI Scoring
               │
               ▼
   Human Review & Validation
               │
               ▼
      Actionable Dataset
```

---

## 3. Scope

### In Scope

* **Generic Entity Model**: Flexible schema supporting people, companies, repositories, and products.
* **Tabular Ingestion**: Parsing and preserving raw tabular inputs (CSV, TSV, JSON) without mutating source data.
* **Autonomous Public Research**: Treating input URLs and identifiers as seeds to discover corroborating facts from legitimate public sources.
* **Evidence-First Extraction**: Extracting data points paired directly with verbatim snippets and source URLs.
* **Deterministic Normalization**: Standardizing titles, company names, and categories prior to AI reasoning.
* **Spring AI Intelligence**: Applying LLM prompts for scoring, categorization, and ranking against user-defined criteria.
* **Human-in-the-Loop Review**: Providing confidence metrics and review capabilities before final export.
* **Clean Data Export**: Exporting verified datasets to CSV, JSON, and database storage.

### Out of Scope

* Scraping authenticated, private, or member-only pages.
* Bypassing CAPTCHAs, bot defenses (e.g., Cloudflare), or paywalls.
* Ungrounded LLM hallucination (inventing facts without empirical evidence).
* Monolithic domain hardcoding (coupling business logic exclusively to one platform).
* Premature distributed infrastructure before the single-entity and batch workflows are proven.

---

## 4. Important Real-World Constraints

### 1. URLs Are Entry Points, Not Pre-Loaded Knowledge
An LLM cannot see what lies behind a URL without active tools. A URL in an input record is strictly a starting seed for research. To acquire facts, the system must actively fetch pages, invoke public search queries, or consult legitimate APIs.

### 2. Public Information Is Often Incomplete
A single public page rarely contains complete entity records. Experience timelines may be partial and skills unlisted. The platform measures success by:
$$\text{Requested Fields} \longrightarrow \text{Retrieved Fields} \longrightarrow \text{Verified Fields} \longrightarrow \text{Confidence}$$
Partial results are accepted; missing fields are recorded as `UNKNOWN`, never assumed.

### 3. The Zero-Hallucination Imperative
If an attribute cannot be substantiated from retrieved evidence, the value must explicitly be set to `UNKNOWN`. An LLM must never invent plausible dates, employers, or skill sets to fill a schema.

### 4. Legal, Ethical, and Technical Access Boundaries
* Respect `robots.txt` directives and site terms of service.
* Never bypass authentication, session cookies, or paywalls.
* Respect upstream API rate limits and queries-per-second quotas.
* Cache external responses aggressively to prevent redundant network traffic.

### 5. Provenance Is Mandatory
Every extracted fact must maintain field-level traceability: verbatim evidence quotation, canonical source URL, and an explicit confidence rating (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`).

---

## Current State & Planned Work

* **Current**: Problem definition, generic scope boundaries, and reality constraints are established.
* **Planned**: Ingest sample tabular connection records into a clean Spring Boot backend to benchmark initial retrieval.
