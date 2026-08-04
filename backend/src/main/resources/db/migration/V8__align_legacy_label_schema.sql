-- 兼容早期数据库缺少标签编码字段的情况。
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE label_schema ADD COLUMN code VARCHAR(50)', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'label_schema' AND column_name = 'code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
