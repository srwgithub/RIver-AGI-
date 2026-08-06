CREATE TABLE IF NOT EXISTS governance_data_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    connection_config TEXT,
    status VARCHAR(30) DEFAULT 'DISABLED',
    last_test_message VARCHAR(500),
    last_test_at TIMESTAMP NULL,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_governance_source_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS governance_metadata_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    business_description VARCHAR(1000),
    metadata_json TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_governance_metadata_version (dataset_id, field_id, version_no),
    INDEX idx_governance_metadata_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS governance_data_lineage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_dataset_id BIGINT,
    target_dataset_id BIGINT,
    task_id BIGINT,
    relation_type VARCHAR(50) NOT NULL,
    relation_detail TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_governance_lineage_source (source_dataset_id),
    INDEX idx_governance_lineage_target (target_dataset_id),
    INDEX idx_governance_lineage_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS governance_cleaning_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    source_dataset_id BIGINT,
    output_dataset_id BIGINT,
    version_no INT NOT NULL,
    config_json TEXT,
    summary_json TEXT,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_governance_cleaning_version (task_id, version_no),
    INDEX idx_governance_cleaning_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
