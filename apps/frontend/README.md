# Data Enrichment & Research Engine — Frontend Prototype

A minimal, production-oriented frontend prototype proving the end-to-end data enrichment and automated research workflow.

---

## 🎯 Product Workflow

The prototype implements the end-to-end lifecycle for sparse datasets:

```
CSV / XLSX Dataset
       ↓
1. Upload (Drag & drop file or 1-click sample dataset)
       ↓
2. Preview (Inspect row count, schema, and raw data)
       ↓
3. Confirm Columns (Map Name, Profile/URL, Company, Role, and Target Entity Type)
       ↓
4. Batch Enrichment (Sequential execution with live progress & status tracking)
       ↓
5. Inspect & Verify (View extracted attributes, confidence tiers, and source snippets)
       ↓
6. Export (Download combined dataset with Enriched_* attributes as CSV or XLSX)
```

---

## 🏗️ Architecture & Core Principles

1. **Zero Business Logic Duplication**:
   - The backend (`research-service:9741`) remains the sole authority for entity discovery, content crawling, AI extraction, corroboration heuristics, confidence tiers, and UNKNOWN/PARTIAL semantics.
   - The frontend acts strictly as a presentation, mapping, and orchestration layer.

2. **Isolated Client Layer**:
   - Components never construct backend URLs directly.
   - All backend communication is routed through `src/services/researchClient.ts` (`ResearchClient`), providing typed contracts and RFC 7807 `ProblemDetail` error parsing.

3. **Lightweight Client-Side Parsing & Export**:
   - Both CSV and Excel (`.xlsx`) parsing and export are handled client-side using `xlsx` (SheetJS).
   - Datasets retain original column order, appending new `Enriched_<attribute>`, `Canonical_Url`, `Enrichment_Status`, and `Enrichment_Entity_Id` columns on export.

4. **Evidence-First Inspection**:
   - Full lineage tracking: every extracted attribute can be traced back to its supporting evidence snippet and original source URL.
   - Clearly flags `UNKNOWN` attributes when the engine cannot verify factual data with high confidence, preventing hallucinations.

---

## 📂 Project Structure

```
apps/frontend/
├── app/
│   ├── globals.css               # Global Tailwind styles
│   ├── layout.tsx                # App shell and root metadata
│   └── page.tsx                  # Main tabs: Batch Enrichment, Single Seed, Catalog
├── components/
│   ├── upload/
│   │   ├── DatasetUpload.tsx     # File dropzone & 1-click sample loader
│   │   └── DatasetPreview.tsx    # Raw dataset tabular preview & stats
│   ├── enrichment/
│   │   ├── ColumnConfirmation.tsx # Identity column mapping & entity type selector
│   │   └── EnrichmentProgress.tsx # Live progress bar and completion counters
│   └── results/
│       ├── EnrichedDatasetTable.tsx # Enriched rows, status pills, and inspect trigger
│       ├── RecordDetailModal.tsx    # Detailed attribute, evidence snippet & source modal
│       └── DatasetExport.tsx        # CSV and XLSX export triggers
├── lib/
│   ├── api.ts                    # Consolidated API boundary, typed contracts & client methods
│   ├── columnDetector.ts         # Heuristics for auto-detecting column headers
│   ├── fileParser.ts             # Browser-based CSV/XLSX file parser & validator
│   └── exporter.ts               # Merges original + enriched attributes into CSV/XLSX
└── public/
    └── samples/
        └── sparse-people-sample.csv # 5-record sample dataset for quick testing
```

---

## 🚀 Getting Started

### 1. Prerequisites
Ensure the backend services are running:
- **Research Service**: `http://localhost:9741`
- **AI Intelligence Service**: `http://localhost:9742`
- **Dataset Service**: `http://localhost:9743`

### 2. Install Dependencies
```bash
npm install
```

### 3. Run Development Server
```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### 4. Build for Production
```bash
npm run build
```

---

## 🧪 Testing with the Sample Dataset

Click the **"Load 5-Record Sample Dataset"** button on the upload screen to immediately test 5 realistic variations:
1. **Name + LinkedIn URL** (Linus Torvalds)
2. **Name + Company + Role** (Guido van Rossum)
3. **Name + Company only** (Satya Nadella)
4. **Name + Personal Website** (Martin Fowler)
5. **Ambiguous / Sparse entity** (Testing `UNKNOWN` handling)
