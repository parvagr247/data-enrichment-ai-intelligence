# V1 Regression Dataset

## Overview
This directory contains the canonical V1 regression dataset: [`v1-regression-dataset.csv`](./v1-regression-dataset.csv).

This dataset provides a deterministic baseline for evaluating the end-to-end enrichment pipeline across varying degrees of input sparsity, entity types, and multi-source web discovery.

---

## Dataset Characteristics
* **Record Count**: 5 entities
* **Entity Types**: `PERSON`
* **Columns**:
  * `Name` (String): Entity legal or public display name.
  * `Profile_URL` (String, optional): Primary anchor URL (e.g. LinkedIn or personal domain).
  * `Company` (String, optional): Employer or affiliated organization.
  * `Title` (String, optional): Stated role or professional headline.
  * `Entity_Type` (String): Entity classification (`PERSON`).

---

## Record Breakdown & Expected Behavior

| Row | Entity Name | Profile URL | Context Signals | Expected Pipeline Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Linus Torvalds** | `https://www.linkedin.com/in/linustorvalds` | `Linux Foundation`, `Creator & Fellow` | **High Confidence Match**: Anchor URL direct match. Extracts current organization, headline, summary with primary source authority. |
| **2** | **Guido van Rossum** | *(empty)* | `Microsoft`, `Distinguished Engineer` | **Multi-Signal Disambiguation**: High confidence match using name + company + role. Generates targeted search queries without anchor URL. |
| **3** | **Satya Nadella** | *(empty)* | `Microsoft` (Company only, no title or URL) | **Contextual Disambiguation**: Resolves executive role through employer context and top domain authority sources. |
| **4** | **Martin Fowler** | `https://martinfowler.com` | `Thoughtworks`, `Author & Speaker` | **Custom Domain Anchor**: Resolves via personal domain authority and corroborating organizational sources. |
| **5** | **John Smith (Cryptic)** | *(empty)* | *(empty)* | **Ambiguous / Low Confidence**: Very common name with zero context signals. EntityResolver assigns `AMBIGUOUS` match status and low confidence; fields not corroborated are marked `UNKNOWN` rather than hallucinated. |

---

## Known Limitations
1. **Live Search Quota / Paywalls**: When using live search providers (Tavily/Google), rate limits or paywalled websites may cause certain sources to return truncated text.
2. **Ambiguity Preservation**: Common names lacking any organizational or geographical context will intentionally yield `PARTIAL` status with `UNKNOWN` attributes to protect against hallucination.
