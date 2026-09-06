-- V2 Migration: Add rich profile, assessment, recommendation, and execution tracking columns
ALTER TABLE entities
    ADD COLUMN execution_status VARCHAR(50) NULL,
    ADD COLUMN execution_message VARCHAR(2048) NULL,
    ADD COLUMN priority_tier VARCHAR(50) NULL,
    ADD COLUMN relevance_score INT NULL,
    ADD COLUMN profile_json LONGTEXT NULL,
    ADD COLUMN assessment_json LONGTEXT NULL,
    ADD COLUMN recommendation_json LONGTEXT NULL,
    ADD COLUMN findings_json LONGTEXT NULL;
