-- Keep the backup record schema compatible with the BackupRecord entity.
SET @backup_error_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'backup_record'
      AND column_name = 'error'
);
SET @backup_error_sql = IF(
    @backup_error_exists = 0,
    'ALTER TABLE backup_record ADD COLUMN error TEXT NULL AFTER size_bytes',
    'SELECT 1'
);
PREPARE backup_error_stmt FROM @backup_error_sql;
EXECUTE backup_error_stmt;
DEALLOCATE PREPARE backup_error_stmt;
