-- RIver AGI 迁移脚本 V3
-- 添加安全扫描增强、加密存储、TLS配置和密钥管理
-- 使用条件式 ALTER 避免与 V1 重复列冲突

-- 更新 sensitive_data_detection 表（条件式）
ALTER TABLE sensitive_data_detection
    MODIFY COLUMN match_type VARCHAR(100) COMMENT '匹配类型：FIELD_NAME_ONLY/CONTENT_ONLY/FIELD_NAME_AND_CONTENT';

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD COLUMN rule_id VARCHAR(50) DEFAULT NULL COMMENT ''规则ID''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND column_name = 'rule_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD COLUMN rule_name VARCHAR(100) DEFAULT NULL COMMENT ''规则名称''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND column_name = 'rule_name');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD COLUMN detection_method VARCHAR(50) DEFAULT ''REGEX'' COMMENT ''检测方法''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND column_name = 'detection_method');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD COLUMN sample_count INT DEFAULT 0 COMMENT ''检测样本数量''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND column_name = 'sample_count');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD COLUMN false_positive_rate DECIMAL(5,4) DEFAULT NULL COMMENT ''误报率''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND column_name = 'false_positive_rate');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD INDEX idx_sensitive_scan_id (scan_task_id)', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND index_name = 'idx_sensitive_scan_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD INDEX idx_sensitive_risk_level (risk_level)', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND index_name = 'idx_sensitive_risk_level');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE sensitive_data_detection ADD INDEX idx_sensitive_type (sensitive_type)', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sensitive_data_detection' AND index_name = 'idx_sensitive_type');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 创建数据加密配置表
CREATE TABLE IF NOT EXISTS encryption_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    algorithm VARCHAR(50) DEFAULT 'AES-256-GCM',
    encryption_key_id BIGINT,
    is_encrypted BOOLEAN DEFAULT FALSE,
    encrypted_at TIMESTAMP NULL,
    decryption_allowed_roles JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table_column (table_name, column_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建安全策略表
CREATE TABLE IF NOT EXISTS security_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    policy_type VARCHAR(50) NOT NULL,
    rules JSON NOT NULL,
    priority INT DEFAULT 0,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建安全事件表
CREATE TABLE IF NOT EXISTS security_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source_ip VARCHAR(50),
    user_id BIGINT,
    username VARCHAR(50),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    event_details JSON,
    action_taken VARCHAR(100),
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP NULL,
    resolved_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_type (event_type),
    INDEX idx_event_severity (severity),
    INDEX idx_event_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建数据脱敏规则表
CREATE TABLE IF NOT EXISTS masking_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    pattern VARCHAR(500) NOT NULL,
    replacement_pattern VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    is_default BOOLEAN DEFAULT FALSE,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rule_name (rule_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入默认脱敏规则（条件式，避免重复）
INSERT IGNORE INTO masking_rule (rule_name, data_type, pattern, replacement_pattern, description, is_default, is_enabled) VALUES
('手机号脱敏', 'PHONE', '1[3-9]\\d{9}', '*** **** ****', '手机号中间4位用*替代', TRUE, TRUE),
('邮箱脱敏', 'EMAIL', '([\\w.]+)@([\\w.]+)', '***@$2', '邮箱用户名部分用*替代', TRUE, TRUE),
('身份证号脱敏', 'ID_CARD', '[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]', '********************', '身份证号全部用*替代', TRUE, TRUE),
('银行卡号脱敏', 'BANK_CARD', '\\d{16,19}', '**** **** **** ****', '银行卡号全部用*替代', TRUE, TRUE),
('姓名脱敏', 'NAME', '[\\u4e00-\\u9fa5]{2,4}', '**', '姓名用**替代', TRUE, TRUE);

-- 创建预测模型配置表
CREATE TABLE IF NOT EXISTS prediction_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    parameters JSON,
    min_data_points INT DEFAULT 5,
    max_forecast_days INT DEFAULT 365,
    supports_seasonality BOOLEAN DEFAULT FALSE,
    supports_multivariate BOOLEAN DEFAULT FALSE,
    performance_metrics JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_name (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建预测偏差告警表
CREATE TABLE IF NOT EXISTS prediction_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    metric_name VARCHAR(100),
    metric_value DOUBLE,
    threshold DOUBLE,
    alert_message VARCHAR(500),
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_task (task_id),
    INDEX idx_alert_type (alert_type),
    INDEX idx_alert_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
