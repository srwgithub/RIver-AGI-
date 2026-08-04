-- =============================================================================
-- RIver AGI 完整 MySQL 建表脚本
-- 数据库: river_agi
-- 字符集: utf8mb4
-- 注意: 若已使用 Flyway 迁移, 此文件仅作参考/手动初始化使用
-- =============================================================================

CREATE DATABASE IF NOT EXISTS river_agi 
  DEFAULT CHARACTER SET utf8mb4 
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE river_agi;

-- =============================================================================
-- 用户与权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    real_name VARCHAR(50),
    status INT DEFAULT 1,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    url VARCHAR(200),
    method VARCHAR(10),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 数据集管理
-- =============================================================================

CREATE TABLE IF NOT EXISTS dataset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    file_type VARCHAR(50),
    file_path VARCHAR(500),
    file_url VARCHAR(500),
    file_size BIGINT,
    row_count INT,
    column_count INT,
    status VARCHAR(20) DEFAULT 'UPLOADED',
    schema_json JSON,
    preview_json JSON,
    profile_json JSON,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dataset_status (status),
    INDEX idx_dataset_tenant (tenant_id),
    INDEX idx_dataset_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dataset_column (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    field_name VARCHAR(200) NOT NULL,
    field_type VARCHAR(50),
    position INT,
    is_sensitive INT DEFAULT 0,
    sensitive_type VARCHAR(50),
    nullable INT DEFAULT 1,
    null_count INT DEFAULT 0,
    distinct_count INT DEFAULT 0,
    sample_values VARCHAR(1000),
    statistics_json JSON,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dataset_column_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dataset_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    profile_json JSON,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dataset_profile_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS data_quality_issue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    issue_type VARCHAR(50),
    column_name VARCHAR(200),
    row_count INT,
    severity VARCHAR(20),
    description TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quality_issue_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 数据质量分析
-- =============================================================================

CREATE TABLE IF NOT EXISTS analysis_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    task_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    result_json JSON,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_task_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS field_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_task_id BIGINT NOT NULL,
    column_name VARCHAR(200),
    statistics_json JSON,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_field_stats_task FOREIGN KEY (analysis_task_id) REFERENCES analysis_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outlier_detection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_task_id BIGINT NOT NULL,
    column_name VARCHAR(200),
    outlier_count INT,
    outlier_indices JSON,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outlier_task FOREIGN KEY (analysis_task_id) REFERENCES analysis_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 标注管理
-- =============================================================================

CREATE TABLE IF NOT EXISTS label_schema (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50),
    description VARCHAR(500),
    color VARCHAR(50),
    icon VARCHAR(100),
    sort_order INT DEFAULT 0,
    parent_id BIGINT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS annotation_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200),
    description VARCHAR(500),
    dataset_id BIGINT NOT NULL,
    label_schema_id BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    total_rows INT DEFAULT 0,
    completed_rows INT DEFAULT 0,
    assigned_annotators INT DEFAULT 0,
    created_by BIGINT,
    quality_score DOUBLE,
    quality_report_json JSON,
    review_count INT DEFAULT 0,
    arbitration_count INT DEFAULT 0,
    pass_rate DOUBLE,
    consistency_rate DOUBLE,
    publish_version VARCHAR(50),
    published_at TIMESTAMP NULL,
    collaboration_mode VARCHAR(20) DEFAULT 'SINGLE',
    review_required BOOLEAN DEFAULT FALSE,
    review_threshold DECIMAL(3,2) DEFAULT 0.80,
    assigned_reviewers JSON,
    quality_checked_rows INT DEFAULT 0,
    passed_rows INT DEFAULT 0,
    failed_rows INT DEFAULT 0,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_annotation_task_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS annotation_task_assignee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    annotator_id BIGINT NOT NULL,
    assigned_by BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_annotation_task_assignee (task_id, annotator_id),
    INDEX idx_annotation_assignee_annotator (annotator_id),
    CONSTRAINT fk_annotation_assignee_task FOREIGN KEY (task_id) REFERENCES annotation_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS annotation_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    dataset_id BIGINT,
    row_index INT,
    label_code VARCHAR(100),
    label_name VARCHAR(200),
    comment TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    annotated_by BIGINT,
    reviewed_by BIGINT,
    review_comment TEXT,
    annotated_at TIMESTAMP NULL,
    reviewed_at TIMESTAMP NULL,
    annotation_type VARCHAR(50),
    confidence DECIMAL(5,4),
    model_source VARCHAR(100),
    rule_version VARCHAR(50),
    is_corrected BOOLEAN DEFAULT FALSE,
    original_confidence DECIMAL(5,4),
    original_label_code VARCHAR(100),
    label_source VARCHAR(100),
    model_version VARCHAR(50),
    review_status VARCHAR(20) DEFAULT 'NOT_REVIEWED',
    corrected_at TIMESTAMP NULL,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_annotation_confidence (confidence),
    INDEX idx_annotation_status (status),
    INDEX idx_annotation_review_status (review_status),
    CONSTRAINT fk_annotation_item_task FOREIGN KEY (task_id) REFERENCES annotation_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    INDEX idx_collaboration_status (status),
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
    INDEX idx_history_operator (operator_id),
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

-- =============================================================================
-- 预测引擎
-- =============================================================================

CREATE TABLE IF NOT EXISTS prediction_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200),
    dataset_id BIGINT NOT NULL,
    target_field VARCHAR(200),
    time_field VARCHAR(200),
    model_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    parameters_json JSON,
    model_version_id BIGINT,
    task_type VARCHAR(20) DEFAULT 'REGRESSION',
    dl_model_id VARCHAR(100),
    forecast_days INT DEFAULT 30,
    confidence_level VARCHAR(20) DEFAULT '0.95',
    window_size INT DEFAULT 7,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_prediction_task_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS model_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100),
    model_type VARCHAR(50),
    algorithm_type VARCHAR(50),
    task_type VARCHAR(20) DEFAULT 'REGRESSION',
    model_path VARCHAR(500),
    version INT DEFAULT 1,
    version_number INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    mae DOUBLE,
    rmse DOUBLE,
    mape DOUBLE,
    training_metrics_json JSON,
    feature_importance_json JSON,
    algorithm_params JSON,
    training_samples BIGINT DEFAULT 0,
    prediction_task_id BIGINT,
    is_production BOOLEAN DEFAULT FALSE,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_version_task (prediction_task_id),
    INDEX idx_model_version_production (is_production)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prediction_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    prediction_date VARCHAR(50),
    predicted_value DOUBLE,
    actual_value DOUBLE,
    lower_bound DOUBLE,
    upper_bound DOUBLE,
    confidence DOUBLE,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prediction_result_task FOREIGN KEY (task_id) REFERENCES prediction_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

-- =============================================================================
-- 安全扫描
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_scan_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    scan_time TIMESTAMP NULL,
    risk_count INT DEFAULT 0,
    total_fields INT DEFAULT 0,
    sensitive_fields_found INT DEFAULT 0,
    high_risk_count INT DEFAULT 0,
    medium_risk_count INT DEFAULT 0,
    low_risk_count INT DEFAULT 0,
    scan_summary_json JSON,
    error_message TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_security_scan_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sensitive_data_detection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scan_task_id BIGINT NOT NULL,
    column_name VARCHAR(200),
    sensitive_type VARCHAR(50),
    risk_level VARCHAR(20),
    detected_count INT,
    sample_data TEXT,
    masked_sample_data TEXT,
    confidence DECIMAL(5,4),
    rule_version VARCHAR(50),
    rule_id VARCHAR(50),
    rule_name VARCHAR(100),
    detection_method VARCHAR(50) DEFAULT 'REGEX',
    sample_count INT DEFAULT 0,
    false_positive_rate DECIMAL(5,4),
    suggestion VARCHAR(500),
    match_type VARCHAR(100),
    regex_pattern VARCHAR(500),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sensitive_scan_id (scan_task_id),
    INDEX idx_sensitive_risk_level (risk_level),
    INDEX idx_sensitive_type (sensitive_type),
    CONSTRAINT fk_sensitive_detection_scan FOREIGN KEY (scan_task_id) REFERENCES security_scan_task(id)
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

-- =============================================================================
-- 数据脱敏
-- =============================================================================

CREATE TABLE IF NOT EXISTS data_mask_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    column_name VARCHAR(200),
    mask_type VARCHAR(50),
    masked_count INT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mask_record_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 审计日志
-- =============================================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    action_type VARCHAR(100),
    resource_type VARCHAR(50),
    resource_id BIGINT,
    resource_name VARCHAR(200),
    user_id BIGINT,
    username VARCHAR(50),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    request_method VARCHAR(10),
    request_path VARCHAR(200),
    operation_details JSON,
    result VARCHAR(20),
    duration_ms BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user_id (user_id),
    INDEX idx_audit_action_type (action_type),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    path VARCHAR(500),
    ip_address VARCHAR(50),
    result VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    trace_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_access_user (user_id),
    INDEX idx_access_resource (resource_type, resource_id),
    INDEX idx_access_result (result),
    INDEX idx_access_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 异步任务
-- =============================================================================

CREATE TABLE IF NOT EXISTS async_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(50) NOT NULL,
    task_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    progress_percent INT DEFAULT 0,
    result_json JSON,
    error_message TEXT,
    error_code VARCHAR(50),
    params_json JSON,
    resource_id BIGINT,
    resource_type VARCHAR(50),
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_async_task_status (status),
    INDEX idx_async_task_type (task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 图表与报告
-- =============================================================================

CREATE TABLE IF NOT EXISTS chart_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    chart_type VARCHAR(50),
    title VARCHAR(200),
    config_json JSON,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chart_config_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    title VARCHAR(200),
    content JSON,
    report_type VARCHAR(50),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- AI 对话
-- =============================================================================

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    dataset_id BIGINT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_chat_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls_json JSON,
    tool_results_json JSON,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 备份与恢复
-- =============================================================================

CREATE TABLE IF NOT EXISTS backup_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    backup_id VARCHAR(100) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    file_path VARCHAR(500),
    size_bytes BIGINT DEFAULT 0,
    error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    UNIQUE KEY uk_backup_id (backup_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 媒体标注
-- =============================================================================

CREATE TABLE IF NOT EXISTS media_annotation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    media_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    duration_seconds BIGINT DEFAULT 0,
    frame_count INT DEFAULT 0,
    annotation_data JSON,
    bounding_boxes JSON,
    key_frames JSON,
    transcription TEXT,
    annotated_by BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    confidence DECIMAL(5,4),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_media_task_id (task_id),
    INDEX idx_media_type (media_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 加密密钥
-- =============================================================================

CREATE TABLE IF NOT EXISTS encryption_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_name VARCHAR(100) NOT NULL UNIQUE,
    key_type VARCHAR(50) NOT NULL,
    encrypted_key_data TEXT NOT NULL,
    algorithm VARCHAR(50),
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NULL,
    last_rotated_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 数据导出
-- =============================================================================

CREATE TABLE IF NOT EXISTS export_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_type VARCHAR(50),
    export_format VARCHAR(20),
    file_path VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    exported_by BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    row_count INT DEFAULT 0,
    masked BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_export_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 初始数据
-- =============================================================================

INSERT INTO sys_role (id, name, code, description) 
SELECT 1000, '管理员', 'ADMIN', '系统管理员' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'ADMIN');

INSERT INTO sys_role (id, name, code, description) 
SELECT 1001, '用户', 'USER', '普通用户' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'USER');

INSERT INTO sys_user (id, username, password, email, real_name, status, tenant_id, created_at, updated_at, deleted) 
SELECT 1000, 'admin', '$2a$10$LOZraF.f/CoSds33d8VV7OcQj2cNEzKs/jcrOrtlC34Yzd6mGrCxu', 'admin@river-agi.com', '系统管理员', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0 
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_user_role (user_id, role_id) 
SELECT 1000, 1000 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 1000);

-- 默认脱敏规则
INSERT INTO masking_rule (rule_name, data_type, pattern, replacement_pattern, description, is_default, is_enabled) VALUES
('手机号脱敏', 'PHONE', '1[3-9]\\d{9}', '*** **** ****', '手机号中间4位用*替代', TRUE, TRUE),
('邮箱脱敏', 'EMAIL', '([\\w.]+)@([\\w.]+)', '***@$2', '邮箱用户名部分用*替代', TRUE, TRUE),
('身份证号脱敏', 'ID_CARD', '[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]', '********************', '身份证号全部用*替代', TRUE, TRUE),
('银行卡号脱敏', 'BANK_CARD', '\\d{16,19}', '**** **** **** ****', '银行卡号全部用*替代', TRUE, TRUE),
('姓名脱敏', 'NAME', '[\\u4e00-\\u9fa5]{2,4}', '**', '姓名用**替代', TRUE, TRUE);
