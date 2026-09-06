-- V3 Migration: Add user_id column to entities for multi-tenant data ownership
ALTER TABLE entities
    ADD COLUMN user_id VARCHAR(36) NULL,
    ADD INDEX idx_entities_user_id (user_id);
