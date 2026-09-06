# Concept 05: Research Provider Abstraction & Multi-Strategy Query Generation

Discovering entity information across public and permitted web sources requires querying external search providers. However, binding the research engine directly to a single provider (e.g., Tavily, Google, Bing) or issuing a single naive query (e.g., `"Jane Doe"`) results in vendor lock-in, poor search precision, duplicate hits, and missed domain-specific facts.

This guide explains the architecture of the **Research Provider Abstraction** and the **Requirement-Aware Multi-Strategy Query Engine** in V2.

---

## 1. The Problem

1. **Vendor Coupling**: Directly calling Tavily or any single search engine across codebase classes creates tight coupling, prevents offline unit testing, and makes switching search providers difficult.
2. **Naive Query Generation**: Simply querying an entity's name retrieves hundreds of irrelevant individuals or generic homepage links.
3. **Requirement Ignorance**: When a user specifically asks to find an entity's "education" or "technologies", a generic query fails to surface the specific resumes, publications, or GitHub profiles containing those facts.
4. **Missing Identity Anchoring**: Disjoint queries for different attributes without anchoring on known entity identifiers (such as company or canonical profile URL) risk finding completely unrelated people or companies.

---

## 2. Core Architecture

The solution couples a pluggable search provider interface with a multi-strategy query generation engine:

```mermaid
flowchart TD
    Req["Target Entity + User Requirements<br/>(e.g., Target Fields: ['role', 'education'])"] --> QB["QueryBuilder"]
    
    subgraph QueryBuilder ["Requirement-Aware Query Generation"]
        QB --> Strat1["Strategy: EXACT_NAME_AND_ORG<br/>('\"Jane Doe\" \"Acme Corp\"')"]
        QB --> Strat2["Strategy: NAME_AND_FIELD<br/>('\"Jane Doe\" education degree university')"]
        QB --> Strat3["Strategy: CANONICAL_DOMAIN<br/>('\"Jane Doe\" site:linkedin.com/in')"]
        QB --> Strat4["Strategy: NAME_AND_INTENT<br/>('\"Jane Doe\" Acme Corp background')"]
    end
    
    Strat1 --> RP["ResearchProvider Interface"]
    Strat2 --> RP
    Strat3 --> RP
    Strat4 --> RP
    
    subgraph Providers ["Provider Implementations"]
        RP --> Tavily["TavilySearchProvider<br/>(Live Web Search API)"]
        RP --> Mock["MockSearchProvider<br/>(Offline / Test Provider)"]
    end
    
    Tavily --> Ranker["SourceRanker & Deduplicator"]
    Mock --> Ranker
```

---

## 3. Pluggable Provider Contract

The `ResearchProvider` interface decouples search execution from external HTTP implementations:

```java
public interface ResearchProvider {
    List<SearchResult> search(String query, int maxResults);
    String providerName();
}
```

The existing `SearchProvider` interface extends `ResearchProvider`, ensuring full backward compatibility with V1 injection points while allowing multiple providers (such as Tavily, Bing, or in-memory Mock implementations) to be selected via Spring configuration profiles (`spring.profiles.active=test` vs `production`).

---

## 4. Query Intent Classification & Strategies

To generate targeted queries, the engine defines explicit query intents and execution strategies:

* **`QueryIntent`**: Categorizes what kind of attribute the search targets:
  - `IDENTITY`: Core identity resolution (full name, canonical profile).
  - `ROLE`: Current job title, executive role, leadership position.
  - `ORGANIZATION`: Current company, previous employers, founding team.
  - `EDUCATION`: Degree, university, alumni records.
  - `TECHNOLOGY`: Programming languages, tech stack, open-source repos.
  - `PRODUCT`: Software offerings, features, pricing models.
  - `LOCATION`: City, state, headquarters, regional presence.
* **`QueryStrategy`**: Specifies the grammatical and syntactical structure of the query:
  - `EXACT_NAME_AND_ORG`: Quoted exact full name + quoted organization.
  - `NAME_AND_FIELD`: Quoted entity name + target field keywords and synonyms.
  - `CANONICAL_DOMAIN`: Scoped search on authoritative domains (`site:linkedin.com/in`, `site:github.com`).
  - `NAME_AND_INTENT`: High-intent contextual queries.

### Identity-Anchored Primary Query
To ensure search hits pertain to the intended subject, the primary query (index 0) is **always anchored** by the entity's full name and known organizational or URL identifier:
```java
// Index 0: Guaranteed Primary Identity Query
queries.add(new ResearchQuery(
    "\"" + entityName + "\" " + identifierKeyword,
    QueryIntent.IDENTITY,
    QueryStrategy.EXACT_NAME_AND_ORG
));
```

Subsequent queries dynamically target missing attributes identified during schema profiling or user-directed requirements.

---

## 5. Benefits & Trade-offs

| Aspect | Monolithic Search | Provider Abstraction & Multi-Strategy Queries |
| :--- | :--- | :--- |
| **Vendor Independence** | Hardcoded to one vendor | Swappable via Spring Bean configuration |
| **Precision** | Low (ambiguous hits) | High (exact quotes + domain scoping) |
| **Target Field Recall** | Moderate | High (custom query generated per missing field) |
| **API Cost Control** | Uncontrolled | Controlled via `maxResults` and deduplication |
| **Offline Testing** | Impossible without mocking network | Zero-network mock providers run in milliseconds |
