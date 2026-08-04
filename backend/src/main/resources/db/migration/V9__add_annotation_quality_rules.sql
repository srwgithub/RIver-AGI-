CREATE TABLE IF NOT EXISTS annotation_quality_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(80) NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    pattern VARCHAR(500),
    threshold DECIMAL(10,4),
    action VARCHAR(30) DEFAULT 'REVIEW',
    priority INT DEFAULT 100,
    enabled BOOLEAN DEFAULT TRUE,
    description VARCHAR(500),
    version VARCHAR(30) DEFAULT '1.0.0',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_annotation_quality_rule_code (tenant_id, code),
    INDEX idx_annotation_quality_rule_enabled (tenant_id, enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO annotation_quality_rule
    (tenant_id, name, code, rule_type, threshold, action, priority, enabled, description)
SELECT 1, '标签合法性', 'LABEL_IN_SCHEMA', 'LABEL_IN_SCHEMA', NULL, 'REVIEW', 10, TRUE, '标注标签必须属于当前任务标签体系'
WHERE NOT EXISTS (SELECT 1 FROM annotation_quality_rule WHERE tenant_id = 1 AND code = 'LABEL_IN_SCHEMA');

INSERT INTO annotation_quality_rule
    (tenant_id, name, code, rule_type, threshold, action, priority, enabled, description)
SELECT 1, '最低置信度', 'MIN_CONFIDENCE', 'MIN_CONFIDENCE', 0.70, 'REVIEW', 20, TRUE, '低于阈值的标注自动进入复核'
WHERE NOT EXISTS (SELECT 1 FROM annotation_quality_rule WHERE tenant_id = 1 AND code = 'MIN_CONFIDENCE');
