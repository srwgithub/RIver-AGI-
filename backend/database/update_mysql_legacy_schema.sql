-- RIver AGI 旧版 MySQL 数据库升级脚本
-- 用于已经存在的 river_agi 数据库，不要在生产库直接执行，建议先备份。
-- 适用：早期 init.sql 创建的数据库缺少当前程序字段的情况。
USE river_agi;

-- 1. 数据分析结果与审计字段
ALTER TABLE analysis_task
    ADD COLUMN IF NOT EXISTS result_json JSON;

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS request_method VARCHAR(10),
    ADD COLUMN IF NOT EXISTS request_path VARCHAR(200),
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT;

-- 2. 标签体系编码字段
ALTER TABLE label_schema
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

-- 3. 预测增强字段
ALTER TABLE prediction_task
    ADD COLUMN IF NOT EXISTS forecast_days INT DEFAULT 30,
    ADD COLUMN IF NOT EXISTS confidence_level VARCHAR(20) DEFAULT '0.95',
    ADD COLUMN IF NOT EXISTS window_size INT DEFAULT 7;

-- 4. 异步任务增强字段
ALTER TABLE async_task
    ADD COLUMN IF NOT EXISTS progress_percent INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(50) DEFAULT NULL;

-- 5. 标注追踪与审核字段
ALTER TABLE annotation_item
    ADD COLUMN IF NOT EXISTS label_source VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS model_version VARCHAR(50) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS original_label VARCHAR(200) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(20) DEFAULT 'NOT_REVIEWED',
    ADD COLUMN IF NOT EXISTS review_note VARCHAR(500) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS reviewed_by BIGINT DEFAULT NULL;

ALTER TABLE annotation_task
    ADD COLUMN IF NOT EXISTS collaboration_mode VARCHAR(20) DEFAULT 'SINGLE',
    ADD COLUMN IF NOT EXISTS review_required BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS review_threshold DECIMAL(3,2) DEFAULT 0.80,
    ADD COLUMN IF NOT EXISTS assigned_reviewers JSON,
    ADD COLUMN IF NOT EXISTS quality_checked_rows INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS passed_rows INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failed_rows INT DEFAULT 0;

-- 6. 安全扫描增强字段
ALTER TABLE sensitive_data_detection
    ADD COLUMN IF NOT EXISTS rule_id VARCHAR(50) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS rule_name VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS detection_method VARCHAR(50) DEFAULT 'REGEX',
    ADD COLUMN IF NOT EXISTS sample_count INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS false_positive_rate DECIMAL(5,4) DEFAULT NULL;

-- 7. 标注协作、历史、质检、仲裁表
CREATE TABLE IF NOT EXISTS annotation_collaboration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    annotator_id BIGINT NOT NULL,
    row_index INT NOT NULL,
    lock_acquired_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    lock_expires_at TIMESTAMP NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    UNIQUE KEY uk_task_row (task_id, row_index),
    INDEX idx_collaboration_annotator (annotator_id),
    CONSTRAINT fk_collaboration_task FOREIGN KEY (task_id) REFERENCES annotation_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS annotation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(50),
    old_value JSON,
    new_value JSON,
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_history_item (item_id),
    INDEX idx_history_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS annotation_quality_check (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    checker_id BIGINT NOT NULL,
    checker_name VARCHAR(50),
    check_result VARCHAR(20) NOT NULL,
    score DECIMAL(3,2),
    issues_found JSON,
    comment VARCHAR(500),
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_quality_check_task (task_id),
    INDEX idx_quality_check_item (item_id),
    CONSTRAINT fk_quality_check_task FOREIGN KEY (task_id) REFERENCES annotation_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS arbitration_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    original_annotator_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    arbitration_result VARCHAR(20) NOT NULL,
    final_label VARCHAR(100),
    final_comment VARCHAR(500),
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_arbitration_task (task_id),
    INDEX idx_arbitration_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 安全策略、事件和脱敏规则表
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

-- 9. 默认标签体系，供标注页面直接选择
INSERT INTO label_schema (name, code, description, sort_order, tenant_id, deleted)
SELECT '通用数据分类', 'DEFAULT_DATA_CLASSIFICATION', '系统默认数据分类标签体系', 0, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'DEFAULT_DATA_CLASSIFICATION' AND parent_id IS NULL);

SET @label_root_id = (
    SELECT id FROM label_schema
    WHERE code = 'DEFAULT_DATA_CLASSIFICATION' AND parent_id IS NULL
    ORDER BY id LIMIT 1
);

INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '手机号', 'phone', @label_root_id, 0, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'phone' AND parent_id = @label_root_id);
INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '邮箱', 'email', @label_root_id, 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'email' AND parent_id = @label_root_id);
INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '身份证号', 'id_card', @label_root_id, 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'id_card' AND parent_id = @label_root_id);
INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '银行卡号', 'bank_card', @label_root_id, 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'bank_card' AND parent_id = @label_root_id);
INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '日期', 'date', @label_root_id, 4, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'date' AND parent_id = @label_root_id);
INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '金额', 'amount', @label_root_id, 5, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'amount' AND parent_id = @label_root_id);
INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '性别', 'gender', @label_root_id, 6, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'gender' AND parent_id = @label_root_id);
INSERT INTO label_schema (name, code, parent_id, sort_order, tenant_id, deleted)
SELECT '地址', 'address', @label_root_id, 7, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM label_schema WHERE code = 'address' AND parent_id = @label_root_id);
