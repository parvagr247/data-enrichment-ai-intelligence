-- Initial relational schema for dataset-service
-- Provides structured persistence for enriched entities, discovered sources, and verified facts.

CREATE TABLE IF NOT EXISTS entities (
    entity_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    canonical_url VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_id),
    INDEX idx_entities_type (entity_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS entity_sources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entity_id VARCHAR(64) NOT NULL,
    source_url VARCHAR(1024) NOT NULL,
    title VARCHAR(512),
    snippet TEXT,
    source_type VARCHAR(50),
    domain VARCHAR(255),
    provider VARCHAR(50),
    relevance DOUBLE,
    retrieved_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    INDEX idx_sources_entity_id (entity_id),
    CONSTRAINT fk_sources_entity FOREIGN KEY (entity_id) REFERENCES entities (entity_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS entity_attributes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entity_id VARCHAR(64) NOT NULL,
    attribute_name VARCHAR(100) NOT NULL,
    attribute_value TEXT NOT NULL,
    source_url VARCHAR(1024),
    evidence_snippet TEXT,
    confidence VARCHAR(50),
    PRIMARY KEY (id),
    INDEX idx_attributes_entity_id (entity_id),
    INDEX idx_attributes_name (attribute_name),
    CONSTRAINT fk_attributes_entity FOREIGN KEY (entity_id) REFERENCES entities (entity_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
