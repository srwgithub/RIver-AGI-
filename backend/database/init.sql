-- RIver AGI 数据库初始化脚本
-- PostgreSQL 15+

-- 用户权限表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    real_name VARCHAR(50),
    status INTEGER DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) UNIQUE NOT NULL,
    resource_type VARCHAR(50),
    resource_path VARCHAR(255),
    sort_order INTEGER DEFAULT 0,
    parent_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES sys_role(id),
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
);

-- 数据管理表
CREATE TABLE IF NOT EXISTS dataset (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    file_type VARCHAR(20),
    file_path VARCHAR(500),
    file_url VARCHAR(1000),
    file_size BIGINT,
    row_count INTEGER,
    column_count INTEGER,
    status VARCHAR(20) DEFAULT 'UPLOADED',
    schema_json TEXT,
    preview_json TEXT,
    profile_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS dataset_column (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(20),
    position INTEGER,
    null_count INTEGER,
    distinct_count INTEGER,
    sample_values TEXT,
    statistics_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS dataset_profile (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    min_value DOUBLE PRECISION,
    max_value DOUBLE PRECISION,
    mean_value DOUBLE PRECISION,
    median_value DOUBLE PRECISION,
    std_dev DOUBLE PRECISION,
    null_count INTEGER,
    total_count INTEGER,
    null_rate DOUBLE PRECISION,
    unique_rate DOUBLE PRECISION,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS data_quality_issue (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    row_index INTEGER,
    value TEXT,
    z_score DOUBLE PRECISION,
    iqr_score DOUBLE PRECISION,
    outlier_type VARCHAR(20),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 标注管理表
CREATE TABLE IF NOT EXISTS label_schema (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    color VARCHAR(20),
    icon VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    parent_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS annotation_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    dataset_id BIGINT NOT NULL,
    label_schema_id BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    total_rows INTEGER,
    completed_rows INTEGER DEFAULT 0,
    assigned_annotators INTEGER DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id),
    FOREIGN KEY (label_schema_id) REFERENCES label_schema(id)
);

CREATE TABLE IF NOT EXISTS annotation_item (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    task_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    row_index INTEGER,
    label_code VARCHAR(100),
    label_name VARCHAR(100),
    comment TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    annotated_by BIGINT,
    reviewed_by BIGINT,
    review_comment TEXT,
    annotated_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (task_id) REFERENCES annotation_task(id),
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- 分析和预测表
CREATE TABLE IF NOT EXISTS analysis_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    task_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    parameters_json TEXT,
    results_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS analysis_result (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    dataset_id BIGINT,
    report_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'COMPLETED',
    content_json TEXT,
    file_url VARCHAR(1000),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS chart_definition (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    chart_type VARCHAR(50),
    title VARCHAR(255),
    x_axis_field VARCHAR(255),
    y_axis_field VARCHAR(255),
    series_fields TEXT,
    config_json TEXT,
    data_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS prediction_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(255),
    dataset_id BIGINT NOT NULL,
    target_field VARCHAR(255),
    time_field VARCHAR(255),
    model_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',
    parameters_json TEXT,
    model_version_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS model_version (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    model_name VARCHAR(255) NOT NULL,
    model_type VARCHAR(50),
    model_path VARCHAR(500),
    version INTEGER DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    mae DOUBLE PRECISION,
    rmse DOUBLE PRECISION,
    mape DOUBLE PRECISION,
    training_metrics_json TEXT,
    feature_importance_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS prediction_result (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    task_id BIGINT NOT NULL,
    prediction_date VARCHAR(50),
    predicted_value DOUBLE PRECISION,
    actual_value DOUBLE PRECISION,
    lower_bound DOUBLE PRECISION,
    upper_bound DOUBLE PRECISION,
    confidence DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (task_id) REFERENCES prediction_task(id)
);

-- 安全和审计表
CREATE TABLE IF NOT EXISTS security_scan (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    total_fields_scanned INTEGER,
    sensitive_fields_found INTEGER,
    scan_summary_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS security_risk (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    field_name VARCHAR(255),
    field_value TEXT,
    sensitive_type VARCHAR(50),
    risk_level VARCHAR(20),
    confidence DOUBLE PRECISION,
    suggestion TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS data_mask_record (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    dataset_id BIGINT NOT NULL,
    field_name VARCHAR(255),
    mask_type VARCHAR(20),
    original_value TEXT,
    masked_value TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    action_type VARCHAR(50),
    resource_type VARCHAR(50),
    resource_id BIGINT,
    resource_name VARCHAR(255),
    user_id BIGINT,
    username VARCHAR(50),
    ip_address VARCHAR(50),
    user_agent TEXT,
    operation_details TEXT,
    result VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 对话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    title VARCHAR(255),
    dataset_id BIGINT,
    user_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls_json TEXT,
    tool_results_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_dataset_created_by ON dataset(created_by);
CREATE INDEX IF NOT EXISTS idx_dataset_column_dataset_id ON dataset_column(dataset_id);
CREATE INDEX IF NOT EXISTS idx_annotation_item_task_id ON annotation_item(task_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_chat_session_user_id ON chat_session(user_id);

-- 插入初始数据

-- 角色
INSERT INTO sys_role (name, code, description) VALUES 
('管理员', 'ADMIN', '系统管理员'),
('分析师', 'ANALYST', '数据分析员'),
('标注员', 'ANNOTATOR', '数据标注员'),
('普通用户', 'USER', '普通用户');

-- 权限
INSERT INTO sys_permission (name, code, resource_type, resource_path, sort_order, parent_id) VALUES 
('用户管理', 'user:manage', 'MENU', '/users', 1, NULL),
('数据集管理', 'dataset:manage', 'MENU', '/datasets', 2, NULL),
('数据分析', 'analysis:view', 'MENU', '/analysis', 3, NULL),
('数据标注', 'annotation:manage', 'MENU', '/annotation', 4, NULL),
('预测管理', 'prediction:manage', 'MENU', '/prediction', 5, NULL),
('安全审计', 'security:view', 'MENU', '/security', 6, NULL),
('图表报告', 'chart:manage', 'MENU', '/charts', 7, NULL),
('AI对话', 'chat:use', 'MENU', '/chat', 8, NULL);

-- 角色权限关联
INSERT INTO sys_role_permission (role_id, permission_id) VALUES 
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8),
(2, 2), (2, 3), (2, 5), (2, 6), (2, 7), (2, 8),
(3, 2), (3, 4),
(4, 2), (4, 3), (4, 8);

-- 默认管理员用户（密码：admin123，BCrypt加密）
INSERT INTO sys_user (username, password, email, real_name, status) VALUES 
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq', 'admin@river-agi.com', '系统管理员', 1);