-- V10: Market Trend Analysis Dashboard Schema
-- 市场趋势分析与可视化看板

-- Dashboard table
CREATE TABLE IF NOT EXISTS dashboard (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    dataset_id BIGINT,
    category VARCHAR(50) DEFAULT 'TREND',
    layout_json JSON,
    filter_config_json JSON,
    is_default TINYINT DEFAULT 0,
    is_public TINYINT DEFAULT 0,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dashboard widgets
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
    config_json JSON,
    data_source_json JSON,
    sort_order INT DEFAULT 0,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_widget_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboard(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Report templates
CREATE TABLE IF NOT EXISTS report_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    dataset_id BIGINT,
    report_type VARCHAR(50) DEFAULT 'TREND',
    sections_json JSON,
    parameters_json JSON,
    schedule_config_json JSON,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Report instances (generated reports)
CREATE TABLE IF NOT EXISTS report_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT,
    dataset_id BIGINT,
    title VARCHAR(300),
    content_json JSON,
    export_format VARCHAR(20) DEFAULT 'JSON',
    file_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'GENERATED',
    generated_by BIGINT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT DEFAULT 1,
    deleted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Trend diagnosis results
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
    turning_points_json JSON,
    decomposition_json JSON,
    trend_summary TEXT,
    tenant_id BIGINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Anomaly alerts
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
    description TEXT,
    status VARCHAR(20) DEFAULT 'OPEN',
    root_cause_hint TEXT,
    tenant_id BIGINT DEFAULT 1,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Root cause analysis
CREATE TABLE IF NOT EXISTS root_cause_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    anomaly_alert_id BIGINT,
    prediction_task_id BIGINT,
    dataset_id BIGINT,
    analysis_type VARCHAR(50),
    target_metric VARCHAR(200),
    impact_value DOUBLE,
    impact_percent DOUBLE,
    factors_json JSON,
    top_contributors_json JSON,
    recommendations_json JSON,
    analysis_summary TEXT,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Decision scenarios (what-if analysis)
CREATE TABLE IF NOT EXISTS decision_scenario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT,
    prediction_task_id BIGINT,
    scenario_name VARCHAR(200) NOT NULL,
    scenario_type VARCHAR(50) DEFAULT 'CUSTOM',
    assumptions_json JSON,
    adjusted_factors_json JSON,
    forecast_results_json JSON,
    expected_growth DOUBLE,
    risk_level VARCHAR(20),
    action_recommendations_json JSON,
    tenant_id BIGINT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default trend dashboard template
INSERT INTO dashboard (name, description, category, is_default, is_public, tenant_id, created_at, updated_at)
SELECT '市场趋势分析看板', '综合趋势预测、对比分析、异常检测、根因分析的多维度可视化看板', 'TREND', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM dashboard WHERE name = '市场趋势分析看板');
