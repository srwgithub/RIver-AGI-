-- V16: 补齐模型版本参数字段。
-- 旧版 MySQL 的 model_version 表没有 algorithm_params，导致模型详情和预测指标查询映射失败。
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE model_version ADD COLUMN algorithm_params JSON DEFAULT NULL COMMENT ''算法参数''',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'model_version' AND column_name = 'algorithm_params');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
