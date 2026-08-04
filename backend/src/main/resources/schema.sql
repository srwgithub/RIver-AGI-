-- 系统用户表
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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    url VARCHAR(200),
    method VARCHAR(10),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 数据集表
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
    schema_json TEXT,
    preview_json TEXT,
    profile_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 数据集字段表
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
    statistics_json TEXT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 数据画像表
CREATE TABLE IF NOT EXISTS dataset_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    profile_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 数据质量问题表
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
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 分析任务表
CREATE TABLE IF NOT EXISTS analysis_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    task_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,
    result_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 字段统计表
CREATE TABLE IF NOT EXISTS field_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_task_id BIGINT NOT NULL,
    column_name VARCHAR(200),
    statistics_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_task_id) REFERENCES analysis_task(id)
);

-- 异常检测表
CREATE TABLE IF NOT EXISTS outlier_detection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_task_id BIGINT NOT NULL,
    column_name VARCHAR(200),
    outlier_count INT,
    outlier_indices TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_task_id) REFERENCES analysis_task(id)
);

-- 标签体系表
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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 标注任务表
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
    quality_report_json TEXT,
    review_count INT DEFAULT 0,
    arbitration_count INT DEFAULT 0,
    pass_rate DOUBLE,
    consistency_rate DOUBLE,
    publish_version VARCHAR(50),
    published_at TIMESTAMP,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS annotation_task_assignee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    annotator_id BIGINT NOT NULL,
    assigned_by BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_annotation_task_assignee (task_id, annotator_id),
    INDEX idx_annotation_assignee_annotator (annotator_id),
    FOREIGN KEY (task_id) REFERENCES annotation_task(id)
);

-- 标注项表
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
    annotated_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    annotation_type VARCHAR(50),
    confidence DECIMAL(5,4),
    model_source VARCHAR(100),
    rule_version VARCHAR(50),
    is_corrected BOOLEAN DEFAULT FALSE,
    original_confidence DECIMAL(5,4),
    original_label_code VARCHAR(100),
    corrected_at TIMESTAMP,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES annotation_task(id)
);

-- 标注质量规则配置表
CREATE TABLE IF NOT EXISTS annotation_quality_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(80) NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    pattern VARCHAR(500),
    threshold DOUBLE,
    action VARCHAR(30) DEFAULT 'REVIEW',
    priority INT DEFAULT 100,
    enabled BOOLEAN DEFAULT TRUE,
    description VARCHAR(500),
    version VARCHAR(30) DEFAULT '1.0.0',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE (tenant_id, code)
);

-- 预测任务表
CREATE TABLE IF NOT EXISTS prediction_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200),
    dataset_id BIGINT NOT NULL,
    target_field VARCHAR(200),
    time_field VARCHAR(200),
    model_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,
    parameters_json TEXT,
    forecast_days INT DEFAULT 30,
    confidence_level VARCHAR(20) DEFAULT '0.95',
    window_size INT DEFAULT 7,
    model_version_id BIGINT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 模型版本表
CREATE TABLE IF NOT EXISTS model_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    model_name VARCHAR(100),
    model_type VARCHAR(50),
    algorithm_type VARCHAR(50),
    task_type VARCHAR(20) DEFAULT 'TIME_SERIES',
    model_path VARCHAR(500),
    version_number INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    mae DOUBLE,
    rmse DOUBLE,
    mape DOUBLE,
    training_metrics_json TEXT,
    algorithm_params TEXT,
    feature_importance_json TEXT,
    training_samples BIGINT DEFAULT 0,
    prediction_task_id BIGINT,
    is_production BOOLEAN DEFAULT FALSE,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 预测结果表
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
    FOREIGN KEY (task_id) REFERENCES prediction_task(id)
);

CREATE TABLE IF NOT EXISTS prediction_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    task_id BIGINT NOT NULL,
    model_version_id BIGINT,
    evaluation_type VARCHAR(40) NOT NULL,
    algorithm VARCHAR(60),
    mae DOUBLE,
    rmse DOUBLE,
    mape DOUBLE,
    r2 DOUBLE,
    bias_percentage DOUBLE,
    accuracy_score DOUBLE,
    status VARCHAR(30) DEFAULT 'PASSED',
    recommendation VARCHAR(500),
    parameters_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 安全扫描任务表
CREATE TABLE IF NOT EXISTS security_scan_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    scan_time TIMESTAMP,
    risk_count INT DEFAULT 0,
    total_fields INT DEFAULT 0,
    sensitive_fields_found INT DEFAULT 0,
    high_risk_count INT DEFAULT 0,
    medium_risk_count INT DEFAULT 0,
    low_risk_count INT DEFAULT 0,
    scan_summary_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);



-- 敏感数据检测表
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
    match_type VARCHAR(50),
    regex_pattern VARCHAR(500),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (scan_task_id) REFERENCES security_scan_task(id)
);

-- 数据脱敏记录表
CREATE TABLE IF NOT EXISTS data_mask_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    column_name VARCHAR(200),
    mask_type VARCHAR(50),
    masked_count INT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 审计日志表
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
    operation_details TEXT,
    result VARCHAR(20),
    duration_ms BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 异步任务表
CREATE TABLE IF NOT EXISTS async_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(50) NOT NULL,
    task_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    result_json TEXT,
    error_message TEXT,
    params_json TEXT,
    resource_id BIGINT,
    resource_type VARCHAR(50),
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 预测运行性能采样与资源快照
CREATE TABLE IF NOT EXISTS performance_sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    prediction_task_id BIGINT,
    model_version_id BIGINT,
    sample_type VARCHAR(30) NOT NULL,
    duration_ms BIGINT,
    latency_ms DECIMAL(16,4),
    throughput_qps DECIMAL(16,4),
    cpu_percent DECIMAL(8,4),
    memory_percent DECIMAL(8,4),
    gpu_percent DECIMAL(8,4),
    storage_io_percent DECIMAL(8,4),
    status VARCHAR(20) DEFAULT 'SUCCESS',
    error_code VARCHAR(100),
    details_json TEXT,
    sampled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_perf_task_time (prediction_task_id, sampled_at),
    INDEX idx_perf_sampled_at (sampled_at)
);

-- 性能阈值与运行失败告警
CREATE TABLE IF NOT EXISTS runtime_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    prediction_task_id BIGINT,
    sample_id BIGINT,
    alert_type VARCHAR(40) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) DEFAULT 'OPEN',
    threshold_json TEXT,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    resolved_by BIGINT,
    resolution VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_runtime_alert_task (prediction_task_id, detected_at),
    INDEX idx_runtime_alert_status (status)
);

-- 图表定义表
CREATE TABLE IF NOT EXISTS chart_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    chart_type VARCHAR(50),
    title VARCHAR(200),
    config_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 报告表
CREATE TABLE IF NOT EXISTS report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    title VARCHAR(200),
    content TEXT,
    report_type VARCHAR(50),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    dataset_id BIGINT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls_json TEXT,
    tool_results_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);

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
    completed_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_backup_id ON backup_record (backup_id);

CREATE TABLE IF NOT EXISTS security_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    policy_type VARCHAR(50) NOT NULL,
    classification VARCHAR(30) DEFAULT 'INTERNAL',
    rules_json TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 媒体标注表（图片/视频/音频）
CREATE TABLE IF NOT EXISTS media_annotation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    media_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    duration_seconds BIGINT DEFAULT 0,
    frame_count INT DEFAULT 0,
    annotation_data TEXT,
    bounding_boxes TEXT,
    key_frames TEXT,
    transcription TEXT,
    annotated_by BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    confidence DECIMAL(5,4),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_media_task_id ON media_annotation (task_id);
CREATE INDEX IF NOT EXISTS idx_media_type ON media_annotation (media_type);

-- 插入初始数据
INSERT INTO sys_role (id, name, code, description) VALUES (1, '管理员', 'ADMIN', '系统管理员');
INSERT INTO sys_role (id, name, code, description) VALUES (2, '用户', 'USER', '普通用户');

INSERT INTO sys_permission (id, name, code, url, method) VALUES (1, '登录', 'auth:login', '/api/v1/auth/login', 'POST');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (2, '获取用户信息', 'auth:me', '/api/v1/auth/me', 'GET');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (3, '数据集列表', 'dataset:list', '/api/v1/datasets', 'GET');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (4, '上传数据集', 'dataset:upload', '/api/v1/datasets/upload', 'POST');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (5, '分析任务列表', 'analysis:list', '/api/v1/analysis', 'GET');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (6, '安全扫描', 'security:scan', '/api/v1/security/datasets/{id}/scan', 'POST');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (7, '审计日志', 'audit:list', '/api/v1/audit/logs', 'GET');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (8, '预测任务', 'prediction:create', '/api/v1/predictions', 'POST');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (9, '标注任务', 'annotation:create', '/api/v1/annotations', 'POST');
INSERT INTO sys_permission (id, name, code, url, method) VALUES (10, '图表生成', 'chart:generate', '/api/v1/charts/generate', 'POST');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 1);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 2);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 3);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 4);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 5);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 6);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 7);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 8);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 9);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 10);

INSERT INTO sys_role_permission (role_id, permission_id) VALUES (2, 1);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (2, 2);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (2, 3);

-- BCrypt 加密的密码: admin123
INSERT INTO sys_user (id, username, password, email, real_name, status, tenant_id, created_at, updated_at, deleted) VALUES (1, 'admin', '$2a$10$LOZraF.f/CoSds33d8VV7OcQj2cNEzKs/jcrOrtlC34Yzd6mGrCxu', 'admin@river-agi.com', '系统管理员', 1, 1, NOW(), NOW(), 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
