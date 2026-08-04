-- V6 is intentionally a no-op. V1 already contains the async_task and
-- audit_log columns. H2 receives the same columns from schema-h2.sql.
-- Keeping this migration preserves version history without duplicate-column
-- failures on a clean MySQL installation.
SELECT 1;
