# Concept 07: Transactional Persistence, Schema Migrations, and Idempotency

Reliable persistence requires that database schema changes are strictly version-controlled, transactions are atomic, and repeated research pipeline runs do not produce duplicate or orphaned records.

---

## 1. Version-Controlled Schema Migrations with Flyway

Relying on `hibernate.ddl-auto: update` in production leads to schema drift, unindexed queries, and potential data corruption. We enforce versioned SQL migrations using **Flyway**:

- Location: `apps/backend/dataset-service/src/main/resources/db/migration/`
- Migration file: `V1__initial_schema.sql`
- Runtime configuration:
  - `spring.flyway.baseline-on-migrate: true`
  - `spring.jpa.hibernate.ddl-auto: validate`

During application startup, Flyway executes pending migrations in strict order and records checksums in `flyway_schema_history`. Hibernate then validates that entity classes match the schema without making alterations.

---

## 2. Idempotent Upsert Mechanics

Entities are identified by a deterministic 64-character SHA-256 `entityId` derived from:
- Canonical URL (e.g., `SHA256("https://github.com/spring-projects/spring-boot")`), OR
- Canonical URN for discovery-first entities (e.g., `SHA256("urn:entity:organization:acme-corporation")`).

When `POST /api/v1/entities` receives a persistence request:
```java
@Transactional
public EnrichedEntity persistEntity(PersistEntityRequest request) {
    EnrichedEntity entity = entityRepository.findById(request.entityId())
            .orElseGet(() -> EnrichedEntity.builder().entityId(request.entityId()).build());

    // Update metadata
    entity.setDisplayName(request.displayName());
    entity.setEntityType(request.entityType());
    entity.setCanonicalUrl(request.canonicalUrl());

    // Atomically replace sources and attributes (orphanRemoval = true)
    entity.getSources().clear();
    // append new sources...

    entity.getAttributes().clear();
    // append new attributes...

    return entityRepository.save(entity);
}
```

### Benefits of Orphan Removal with Clear & Append:
1. **Idempotency**: Running the pipeline twice for the same entity updates the existing record rather than inserting duplicates.
2. **Freshness**: Stale sources or out-of-date attributes from previous runs are automatically cleaned up.
3. **Cascade Deletion**: If an entity is deleted, all associated `entity_sources` and `entity_attributes` are cascaded cleanly.
