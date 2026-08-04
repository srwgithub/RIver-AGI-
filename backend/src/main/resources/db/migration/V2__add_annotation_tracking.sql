-- RIver AGI 迁移脚本 V2
-- 添加标注追踪字段、审核机制和协作支持
-- 使用条件式 ALTER 避免与 V1 重复列冲突

-- 为 annotation_item 添加标签来源追踪字段（条件式，避免重复列）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN label_source VARCHAR(100) DEFAULT NULL COMMENT ''标签来源：RULE_ENGINE/AI_MODEL/MANUAL''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'label_source');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN model_version VARCHAR(50) DEFAULT NULL COMMENT ''AI模型版本''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'model_version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN rule_version VARCHAR(50) DEFAULT NULL COMMENT ''规则引擎版本''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'rule_version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN original_label VARCHAR(200) DEFAULT NULL COMMENT ''原始标签（纠正前）''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'original_label');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN review_status VARCHAR(20) DEFAULT ''NOT_REVIEWED'' COMMENT ''审核状态''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'review_status');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN review_note VARCHAR(500) DEFAULT NULL COMMENT ''审核备注''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'review_note');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN reviewed_at TIMESTAMP NULL COMMENT ''审核时间''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'reviewed_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD COLUMN reviewed_by BIGINT DEFAULT NULL COMMENT ''审核人ID''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND column_name = 'reviewed_by');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 为 annotation_item 添加索引（条件式）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD INDEX idx_annotation_confidence (confidence)', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND index_name = 'idx_annotation_confidence');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD INDEX idx_annotation_status (status)', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND index_name = 'idx_annotation_status');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_item ADD INDEX idx_annotation_review_status (review_status)', 'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'annotation_item' AND index_name = 'idx_annotation_review_status');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 创建标注协作表
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

-- 创建标注历史表
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

-- 创建标注质检表
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

-- 创建仲裁记录表
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

-- 更新 annotation_task 表添加协作相关字段（条件式）
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_task ADD COLUMN collaboration_mode VARCHAR(20) DEFAULT ''SINGLE'' COMMENT ''协作模式''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_task' AND column_name = 'collaboration_mode');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_task ADD COLUMN review_required BOOLEAN DEFAULT FALSE COMMENT ''是否需要审核''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_task' AND column_name = 'review_required');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_task ADD COLUMN review_threshold DECIMAL(3,2) DEFAULT 0.80 COMMENT ''审核阈值''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_task' AND column_name = 'review_threshold');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_task ADD COLUMN assigned_reviewers JSON COMMENT ''分配的审核人列表''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_task' AND column_name = 'assigned_reviewers');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_task ADD COLUMN quality_checked_rows INT DEFAULT 0 COMMENT ''已质检行数''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_task' AND column_name = 'quality_checked_rows');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_task ADD COLUMN passed_rows INT DEFAULT 0 COMMENT ''通过质检行数''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_task' AND column_name = 'passed_rows');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE annotation_task ADD COLUMN failed_rows INT DEFAULT 0 COMMENT ''未通过质检行数''', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_task' AND column_name = 'failed_rows');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
