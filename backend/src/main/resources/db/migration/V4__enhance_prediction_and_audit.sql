-- RIver AGI 迁移脚本 V4
-- 增强预测任务参数化、权限审计和异步任务状态管理
-- 使用条件式 ALTER 避免与 V1 重复列冲突

-- 为 prediction_task 添加自定义预测参数（条件式）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE prediction_task ADD COLUMN forecast_days INT DEFAULT 30 COMMENT ''预测天数''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'prediction_task' AND column_name = 'forecast_days');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE prediction_task ADD COLUMN confidence_level VARCHAR(20) DEFAULT ''0.95'' COMMENT ''置信区间''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'prediction_task' AND column_name = 'confidence_level');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE prediction_task ADD COLUMN window_size INT DEFAULT 7 COMMENT ''滑动窗口大小''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'prediction_task' AND column_name = 'window_size');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 为 async_task 添加进度、错误信息和结束时间字段（条件式）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE async_task ADD COLUMN progress_percent INT DEFAULT 0 COMMENT ''进度百分比''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'async_task' AND column_name = 'progress_percent');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE async_task ADD COLUMN finished_at TIMESTAMP NULL COMMENT ''结束时间''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'async_task' AND column_name = 'finished_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE async_task ADD COLUMN error_code VARCHAR(50) DEFAULT NULL COMMENT ''错误码''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'async_task' AND column_name = 'error_code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 创建资源访问审计表
CREATE TABLE IF NOT EXISTS resource_access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    path VARCHAR(500),
    ip_address VARCHAR(50),
    result VARCHAR(20) NOT NULL COMMENT 'ALLOWED/DENIED',
    error_message VARCHAR(500),
    trace_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_access_user (user_id),
    INDEX idx_access_resource (resource_type, resource_id),
    INDEX idx_access_result (result),
    INDEX idx_access_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
