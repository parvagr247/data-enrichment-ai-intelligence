# Concept 11: Dataset Ingestion, Schema Detection & Multi-Tier Entity Normalization

Enterprise data enrichment rarely begins with a clean, structured JSON API payload. It starts with sparse, messy spreadsheets: CSV or XLSX files exported from CRM systems, applicant trackers, or sales lead databases. These files have inconsistent column headers, missing identifiers, noisy formatting, and arbitrary custom fields.

This guide explains how this platform ingests raw tabular datasets, automatically detects schema identity anchors, and maintains strict separation across **6 distinct data tiers** along the enrichment pipeline.

---

## Why This Exists

In a data enrichment engine, conflating the user's raw input with machine-extracted facts is disastrous:
1. If an enrichment step accidentally overwrites the user's original columns, the user loses their own source data upon export.
2. If the system forces every input file into a rigid pre-baked schema, users cannot enrich arbitrary datasets with bespoke business columns (e.g. `Notes`, `Internal_Score`, `Lead_Owner`).
3. If web-discovered data, AI hypotheses, and user inputs are lumped into a single monolithic object, debugging why a field was set or verifying evidence becomes impossible.

---

## The Problem

A naive implementation typically suffers from:
* **The "Giant Universal DTO" Anti-Pattern**: Creating a single 40-field class `EntityData` used at every stage of the pipeline. Some fields are populated on upload, some during search, some by AI, and some on save. At any given point, it is unclear which fields are trusted, which are guesses, and which were provided by the user.
* **Brittle Header Matching**: Hardcoding exact column name checks (`row.get("company_name")`). If the user's CSV header is `Company`, `Employer`, `Current Org`, or `Organization Name`, the lookup returns `null`.
* **In-Memory File Bloat**: Uploading multi-megabyte files directly into server heap memory without client-side safety limits, risking denial-of-service and Out-Of-Memory errors on the backend.

---

## The Core Idea

1. **Client-Side File Parsing & Safety Capping**: Ingestion runs browser-side via SheetJS ([`fileParser.ts`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/frontend/lib/fileParser.ts)). Files are validated, empty rows are stripped, and row bounds are capped (e.g. 50 records for prototype safety) before any network payload is constructed.
2. **Heuristic Identity Anchor Detection**: Heuristic regex patterns ([`columnDetector.ts`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/frontend/lib/columnDetector.ts)) identify identity anchors (`name`, `url`, `role`, `organization`, `entityType`) while treating all other columns as transparent metadata.
3. **The 6-Tier Data Representation Lifecycle**: Rather than one universal object, data moves through six distinct stages:
   $$\text{Raw User Row} \longrightarrow \text{Normalized Identity} \longrightarrow \text{Discovered Evidence} \longrightarrow \text{Evidence Tuples} \longrightarrow \text{AI Synthesis} \longrightarrow \text{Enriched Dataset}$$

---

## How This Project Uses It

```
apps/frontend/
├── lib/
│   ├── fileParser.ts           # SheetJS parsing, CSV/XLSX streaming, row bounds
│   ├── columnDetector.ts       # Regex heuristics for anchor columns
│   └── exporter.ts             # Merges raw inputs + enriched attributes into CSV/XLSX
├── components/
│   ├── upload/DatasetUpload.tsx    # Drag-and-drop & sample dataset loader
│   ├── upload/DatasetPreview.tsx   # First 15 rows tabular preview
│   └── enrichment/ColumnConfirmation.tsx # User confirmation of detected mappings
```

### 1. Heuristic Column Detection

In [`columnDetector.ts`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/frontend/lib/columnDetector.ts), regex patterns match common variations of entity identity anchors:

```typescript
const NAME_PATTERNS = [/^(full_?name|name|person_?name|contact|employee)$/i, /name/i];
const URL_PATTERNS = [/^(url|website|link|profile_?url|linkedin|linkedin_?url|github_?url)$/i, /url|link|profile/i];
const ORG_PATTERNS = [/^(company|organization|org|company_?name|employer)$/i, /company|org/i];
const ROLE_PATTERNS = [/^(role|title|job_?title|position|designation)$/i, /role|title|position/i];

export function detectColumns(headers: string[]): ColumnMapping {
  return {
    nameColumn: findBestMatch(headers, NAME_PATTERNS),
    urlColumn: findBestMatch(headers, URL_PATTERNS),
    organizationColumn: findBestMatch(headers, ORG_PATTERNS),
    roleColumn: findBestMatch(headers, ROLE_PATTERNS),
    entityTypeColumn: findBestMatch(headers, ENTITY_TYPE_PATTERNS),
  };
}
```

The user reviews and confirms these detected mappings in the UI ([`ColumnConfirmation.tsx`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/frontend/components/enrichment/ColumnConfirmation.tsx)), ensuring the system never acts on incorrect assumptions.

---

## The 6 Distinct Data Tiers

To maintain strict provenance, data is never merged into a single generic bucket. The platform distinguishes:

```
+-----------------------------------------------------------------------------+
| 1. RAW USER ROW (Map<String, String>)                                       |
|    Unaltered input from the user's file (e.g. "Full Name": "Linus", "Notes": "VIP") |
+--------------------------------------+--------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------+
| 2. NORMALIZED IDENTITY (ResearchTarget)                                     |
|    Canonical URL + clean displayName + deterministic SHA-256 entityId        |
+--------------------------------------+--------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------+
| 3. DISCOVERED WEB SOURCES & DOCUMENTS (DiscoveredSource, ExtractedDocument)  |
|    Ranked URLs + clean body text with noise/scripts stripped via Jsoup      |
+--------------------------------------+--------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------+
| 4. VERIFIED EVIDENCE TUPLES (EvidenceTuple)                                 |
|    Attribute value + exact source URL + verbatim snippet + ConfidenceTier    |
+--------------------------------------+--------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------+
| 5. AI-SYNTHESIZED ENRICHMENT (EnrichedAttributeResult)                      |
|    Interpreted facts with status (VERIFIED/CONFLICT/UNRESOLVED)              |
+--------------------------------------+--------------------------------------+
                                       |
                                       v
+-----------------------------------------------------------------------------+
| 6. FINAL ENRICHED DATASET RECORD (RowEnrichmentResult / Export File)         |
|    Original user columns PRESERVED + Enriched_<field> columns APPENDED      |
+-----------------------------------------------------------------------------+
```

### 2. Preserving Lineage During Export

In [`exporter.ts`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/frontend/lib/exporter.ts), the export generator appends new enrichment columns while keeping the user's raw fields completely intact:

```typescript
export function generateExportData(records: EnrichedRecord[]): Record<string, any>[] {
  return records.map((rec) => {
    // 1. Retain all original user-uploaded columns
    const row: Record<string, any> = { ...rec.rawRow };

    // 2. Append pipeline metadata
    row['Enrichment_Status'] = rec.status;
    row['Enrichment_Entity_Id'] = rec.entityId || '';
    row['Canonical_Url'] = rec.canonicalUrl || '';

    // 3. Append enriched attributes with 'Enriched_' prefix
    for (const [key, attr] of Object.entries(rec.attributes)) {
      row[`Enriched_${key}`] = attr.value;
      row[`Enriched_${key}_Confidence`] = attr.confidence;
    }

    return row;
  });
}
```

---

## Important Design Decisions

1. **Browser-Side Ingestion Over Server Multipart Upload**:
   Parsing files in the browser via SheetJS eliminates the need to transmit large raw CSV/XLSX binaries across the backend network. The frontend extracts row maps, runs column detection, and allows the user to preview the data before a single API call is made.
2. **Column Names as Dynamic Key-Value Maps**:
   Raw rows are modeled as `Map<String, String>` rather than a strongly typed Java class. This allows the platform to enrich datasets with 5 columns or 50 columns without requiring database migrations or DTO code changes for each new dataset layout.
3. **Explicit Prefixes on Enriched Output**:
   When exporting enriched datasets, enriched values are prefixed (`Enriched_role`, `Enriched_organization`). This prevents accidental overwriting if the user's input already contained a column named `role`.

---

## Alternatives

| Approach | Why We Did Not Choose It |
| :--- | :--- |
| **Server-Side File Upload (`/api/v1/datasets/upload`)** | Requires managing temporary disk storage, file upload size limits, multipart parsing, and multipart security vulnerabilities on backend instances. |
| **Enforcing a Mandatory Fixed CSV Template** | High user friction. Users must manually reformat their spreadsheets to match exact column names before uploading. Heuristic detection eliminates this burden. |
| **Single Universal Record DTO** | Loses data lineage. Makes it impossible to trace whether a value came from the user's input, a web source, or an AI deduction. |

---

## Common Mistakes

1. **Mutating the Input Row During Enrichment**:
   Modifying `rec.rawRow.put("role", newRole)` destroys the user's original data. If the enrichment was wrong or partial, the user has no way to see what was originally in their spreadsheet.
2. **Treating Unknown Columns as Errors**:
   Rejecting a file because it contains unexpected columns like `Internal_ID` or `Sales_Region` makes the system unusable for real enterprise workflows. Retain unknown columns transparently.
3. **Skipping Identity Anchor Validation**:
   Allowing a row with no name and no URL to proceed to research wastes API calls and produces `FAILED` records. Always validate that at least one identity anchor exists before dispatching jobs.

---

## Production Considerations

* **Streaming Parser for Large Datasets**: In production, datasets with 100,000+ rows cannot be parsed in browser memory. A chunked streaming architecture (e.g. Papaparse chunking or S3 presigned upload with AWS Lambda processing) should be introduced for files exceeding 10MB.
* **Deduplication Across Rows**: Often, the same company or person appears multiple times in a single dataset. Deduplicating rows by `canonicalUrl` before executing research cuts web search and AI costs substantially.
* **Audit Logging for Compliance**: In regulated industries (finance, healthcare), maintaining the immutable separation between original user inputs and machine-generated outputs is required for compliance and auditability.

---

## What I Should Learn From This

1. **Never mutate raw user data; treat the original input as immutable.**
2. **Separate data into explicit lifecycle stages (raw input, normalized identity, evidence, enriched result) rather than using a universal DTO.**
3. **Use heuristic detection to reduce user friction, but always provide a confirmation step before initiating expensive operations.**
4. **Export enriched datasets by appending distinct, prefixed columns rather than overwriting existing headers.**

---

**Previous:** [Concept 10: Spring AI Model Abstraction, Prompt Engineering & Deterministic Fallbacks](10-spring-ai-model-abstraction-and-prompt-engineering.md) | **Next:** [Concept 12: User-Directed Requirements vs. Default Enrichment & Adaptive Scoping](12-user-directed-requirements-and-adaptive-enrichment.md)
