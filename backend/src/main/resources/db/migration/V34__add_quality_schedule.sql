CREATE TABLE IF NOT EXISTS governance_quality_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    interval_minutes INT NOT NULL DEFAULT 1440,
    enabled TINYINT NOT NULL DEFAULT 1,
    last_run_at TIMESTAMP NULL,
    next_run_at TIMESTAMP NULL,
    last_status VARCHAR(30),
    last_error VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_quality_schedule_due (enabled, next_run_at),
    INDEX idx_quality_schedule_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
