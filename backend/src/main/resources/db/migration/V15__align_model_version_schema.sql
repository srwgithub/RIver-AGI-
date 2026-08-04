-- V15: 对齐旧版 MySQL 的 model_version / prediction_task 字段
-- 兼容已存在的 river_agi 数据库；每个字段仅在不存在时添加。

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN version_number INT DEFAULT 1 COMMENT ''模型版本号''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'version_number');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN algorithm_type VARCHAR(50) DEFAULT NULL COMMENT ''算法类型''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'algorithm_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN training_samples BIGINT DEFAULT 0 COMMENT ''训练样本数''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'training_samples');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN prediction_task_id BIGINT DEFAULT NULL COMMENT ''关联预测任务''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'prediction_task_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN is_production BOOLEAN DEFAULT FALSE COMMENT ''是否生产版本''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'is_production');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'updated_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE prediction_task ADD COLUMN task_type VARCHAR(20) DEFAULT ''REGRESSION'' COMMENT ''任务类型''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'prediction_task' AND column_name = 'task_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE prediction_task ADD COLUMN dl_model_id VARCHAR(100) DEFAULT NULL COMMENT ''深度学习模型 ID''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'prediction_task' AND column_name = 'dl_model_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧版表使用 version，新版实体使用 version_number；保留旧字段并迁移已有值。
SET @has_legacy_version = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'version');
SET @sql = IF(@has_legacy_version > 0,
    'UPDATE model_version SET version_number = CASE WHEN version IS NOT NULL AND (version_number IS NULL OR version_number = 1) THEN version ELSE COALESCE(version_number, 1) END',
    'UPDATE model_version SET version_number = COALESCE(version_number, 1)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD INDEX idx_model_version_task (prediction_task_id)',
    'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND index_name = 'idx_model_version_task');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD INDEX idx_model_version_production (is_production)',
    'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND index_name = 'idx_model_version_production');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
