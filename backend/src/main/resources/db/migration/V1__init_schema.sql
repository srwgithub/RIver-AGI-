-- RIver AGI 数据库初始化脚本 V1
-- 适用于 MySQL 8.0+

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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_annotation_task_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id)
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
    corrected_at TIMESTAMP NULL,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_annotation_item_task FOREIGN KEY (task_id) REFERENCES annotation_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    model_path VARCHAR(500),
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    mae DOUBLE,
    rmse DOUBLE,
    mape DOUBLE,
    training_metrics_json JSON,
    feature_importance_json JSON,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    suggestion VARCHAR(500),
    match_type VARCHAR(100),
    regex_pattern VARCHAR(500),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sensitive_detection_scan FOREIGN KEY (scan_task_id) REFERENCES security_scan_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS async_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(50) NOT NULL,
    task_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    result_json JSON,
    error_message TEXT,
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_async_task_status (status),
    INDEX idx_async_task_type (task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    dataset_id BIGINT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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

-- 备份记录表
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

-- 媒体标注表
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

-- 加密密钥表
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

-- 数据导出记录表
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
