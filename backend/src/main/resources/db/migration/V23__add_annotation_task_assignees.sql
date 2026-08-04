CREATE TABLE IF NOT EXISTS annotation_task_assignee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    annotator_id BIGINT NOT NULL,
    assigned_by BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_annotation_task_assignee (task_id, annotator_id),
    INDEX idx_annotation_assignee_annotator (annotator_id),
    CONSTRAINT fk_annotation_assignee_task
        FOREIGN KEY (task_id) REFERENCES annotation_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
