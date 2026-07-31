-- RIver AGI H2 数据库初始化脚本（开发环境）
-- H2 不支持 MySQL 特有语法，需要独立的 schema

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    real_name VARCHAR(50),
    status INT DEFAULT 1,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL,
    url VARCHAR(200),
    method VARCHAR(10),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS analysis_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    task_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    result_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS label_schema (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50),
    description VARCHAR(500),
    color VARCHAR(50),
    sort_order INT DEFAULT 0,
    parent_id BIGINT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS annotation_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200),
    dataset_id BIGINT NOT NULL,
    label_schema_id BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    total_rows INT DEFAULT 0,
    completed_rows INT DEFAULT 0,
    created_by BIGINT,
    quality_score DOUBLE,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS annotation_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    dataset_id BIGINT,
    row_index INT,
    label_code VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',
    annotated_by BIGINT,
    annotation_type VARCHAR(50),
    confidence DECIMAL(5,4),
    model_source VARCHAR(100),
    rule_version VARCHAR(50),
    original_label VARCHAR(200),
    label_source VARCHAR(100),
    model_version VARCHAR(50),
    review_status VARCHAR(20) DEFAULT 'NOT_REVIEWED',
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS prediction_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200),
    dataset_id BIGINT NOT NULL,
    target_field VARCHAR(200),
    time_field VARCHAR(200),
    model_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    parameters_json TEXT,
    forecast_days INT DEFAULT 30,
    confidence_level VARCHAR(20) DEFAULT '0.95',
    window_size INT DEFAULT 7,
    model_version_id BIGINT,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100),
    model_type VARCHAR(50),
    algorithm_type VARCHAR(50),
    task_type VARCHAR(50) DEFAULT 'TIME_SERIES',
    model_path VARCHAR(500),
    version_number INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    mae DOUBLE,
    rmse DOUBLE,
    mape DOUBLE,
    training_metrics_json CLOB,
    algorithm_params CLOB,
    feature_importance_json CLOB,
    training_samples BIGINT DEFAULT 0,
    prediction_task_id BIGINT,
    is_production BOOLEAN DEFAULT FALSE,
    created_by BIGINT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

CREATE TABLE IF NOT EXISTS prediction_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    prediction_date VARCHAR(50),
    predicted_value DOUBLE,
    lower_bound DOUBLE,
    upper_bound DOUBLE,
    confidence DOUBLE,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    error_message TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    detection_method VARCHAR(50),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS async_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(50) NOT NULL,
    task_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    result_json TEXT,
    error_message TEXT,
    params_json TEXT,
    resource_type VARCHAR(50),
    resource_id BIGINT,
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

CREATE TABLE IF NOT EXISTS report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    title VARCHAR(200),
    content TEXT,
    report_type VARCHAR(50),
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls_json TEXT,
    tool_results_json TEXT,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS encryption_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_name VARCHAR(100) NOT NULL,
    key_type VARCHAR(50) NOT NULL,
    encrypted_key_data TEXT NOT NULL,
    algorithm VARCHAR(50),
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (key_name)
);

CREATE TABLE IF NOT EXISTS security_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source_ip VARCHAR(50),
    user_id BIGINT,
    event_details TEXT,
    action_taken VARCHAR(100),
    is_resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始数据（使用较大的ID避免与AUTO_INCREMENT冲突）
INSERT INTO sys_role (id, name, code, description) 
SELECT 1000, '管理员', 'ADMIN', '系统管理员' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'ADMIN');
INSERT INTO sys_role (id, name, code, description) 
SELECT 1001, '用户', 'USER', '普通用户' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'USER');

INSERT INTO sys_user (id, username, password, email, real_name, status, tenant_id, created_at, updated_at, deleted) 
SELECT 1000, 'admin', '$2a$10$LOZraF.f/CoSds33d8VV7OcQj2cNEzKs/jcrOrtlC34Yzd6mGrCxu', 'admin@river-agi.com', '系统管理员', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0 
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_user_role (user_id, role_id) 
SELECT 1000, 1000 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 1000);

-- V13: 趋势分析与可视化看板表
CREATE TABLE IF NOT EXISTS dashboard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    dataset_id BIGINT,
    category VARCHAR(50) DEFAULT 'TREND',
    layout_json CLOB,
    filter_config_json CLOB,
    is_default TINYINT DEFAULT 0,
    is_public TINYINT DEFAULT 0,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dashboard_widget (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dashboard_id BIGINT NOT NULL,
    widget_type VARCHAR(50) NOT NULL,
    title VARCHAR(200),
    chart_type VARCHAR(50),
    position_x INT DEFAULT 0,
    position_y INT DEFAULT 0,
    width INT DEFAULT 6,
    height INT DEFAULT 4,
    config_json CLOB,
    data_source_json CLOB,
    sort_order INT DEFAULT 0,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS report_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    dataset_id BIGINT,
    report_type VARCHAR(50) DEFAULT 'TREND',
    sections_json CLOB,
    parameters_json CLOB,
    schedule_config_json CLOB,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS report_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT,
    dataset_id BIGINT,
    title VARCHAR(300),
    content_json CLOB,
    export_format VARCHAR(20) DEFAULT 'JSON',
    file_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'GENERATED',
    generated_by BIGINT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trend_diagnosis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prediction_task_id BIGINT,
    dataset_id BIGINT,
    target_field VARCHAR(200),
    trend_direction VARCHAR(30),
    trend_slope DOUBLE,
    trend_strength DOUBLE,
    r_squared DOUBLE,
    seasonality_status VARCHAR(30),
    seasonal_period INT,
    seasonal_strength DOUBLE,
    volatility_level VARCHAR(20),
    volatility_coefficient DOUBLE,
    turning_points_json CLOB,
    decomposition_json CLOB,
    trend_summary CLOB,
    tenant_id BIGINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS anomaly_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prediction_task_id BIGINT,
    dataset_id BIGINT,
    anomaly_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    dimension VARCHAR(100),
    anomaly_date VARCHAR(20),
    actual_value DOUBLE,
    predicted_value DOUBLE,
    deviation_percent DOUBLE,
    expected_lower_bound DOUBLE,
    expected_upper_bound DOUBLE,
    description CLOB,
    status VARCHAR(20) DEFAULT 'OPEN',
    root_cause_hint CLOB,
    tenant_id BIGINT DEFAULT 1,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS root_cause_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    anomaly_alert_id BIGINT,
    prediction_task_id BIGINT,
    dataset_id BIGINT,
    analysis_type VARCHAR(50),
    target_metric VARCHAR(200),
    impact_value DOUBLE,
    impact_percent DOUBLE,
    factors_json CLOB,
    top_contributors_json CLOB,
    recommendations_json CLOB,
    analysis_summary CLOB,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS decision_scenario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT,
    prediction_task_id BIGINT,
    scenario_name VARCHAR(200) NOT NULL,
    scenario_type VARCHAR(50) DEFAULT 'CUSTOM',
    assumptions_json CLOB,
    adjusted_factors_json CLOB,
    forecast_results_json CLOB,
    expected_growth DOUBLE,
    risk_level VARCHAR(20),
    action_recommendations_json CLOB,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO dashboard (name, description, category, is_default, is_public, tenant_id, created_at, updated_at)
SELECT '市场趋势分析看板', '综合趋势预测、对比分析、异常检测、根因分析的多维度可视化看板', 'TREND', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM dashboard WHERE name = '市场趋势分析看板');
