-- A namespace has one active configuration plus many immutable snapshots.
-- The legacy schema incorrectly made namespace globally unique.
SET @uk_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'system_config' AND index_name = 'uk_namespace'
);
SET @drop_uk_sql := IF(@uk_exists = 1,
    'ALTER TABLE system_config DROP INDEX uk_namespace', 'SELECT 1');
PREPARE drop_uk_stmt FROM @drop_uk_sql;
EXECUTE drop_uk_stmt;
DEALLOCATE PREPARE drop_uk_stmt;

-- Reserve the same version number independently for active and snapshot rows.
SET @compound_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'system_config' AND index_name = 'uk_system_config_version'
);
SET @add_compound_sql := IF(@compound_exists = 0,
    'ALTER TABLE system_config ADD UNIQUE KEY uk_system_config_version (tenant_id, namespace, snapshot, version)',
    'SELECT 1');
PREPARE add_compound_stmt FROM @add_compound_sql;
EXECUTE add_compound_stmt;
DEALLOCATE PREPARE add_compound_stmt;
