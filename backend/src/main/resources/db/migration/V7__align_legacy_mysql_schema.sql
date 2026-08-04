-- 兼容早期 init.sql 创建的 MySQL 数据库，按列是否存在动态补齐字段。
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE analysis_task ADD COLUMN result_json JSON', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'analysis_task' AND column_name = 'result_json');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE audit_log ADD COLUMN request_method VARCHAR(10)', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'request_method');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE audit_log ADD COLUMN request_path VARCHAR(200)', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'request_path');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE audit_log ADD COLUMN duration_ms BIGINT', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'duration_ms');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
