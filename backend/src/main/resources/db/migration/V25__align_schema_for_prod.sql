-- Align schema for production: add missing columns to match entity definitions
-- Uses stored procedure to safely add columns only if they don't already exist (MySQL compatible)

DELIMITER //
DROP PROCEDURE IF EXISTS add_column_if_missing//
CREATE PROCEDURE add_column_if_missing(
    IN table_name_param VARCHAR(100),
    IN column_name_param VARCHAR(100),
    IN column_definition VARCHAR(500)
)
BEGIN
    DECLARE col_count INT;
    SELECT COUNT(*) INTO col_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_param
      AND column_name = column_name_param;
    IF col_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', table_name_param, ' ADD COLUMN ', column_name_param, ' ', column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

-- collection_task: add 11 missing columns
CALL add_column_if_missing('collection_task', 'media_type', 'VARCHAR(50)');
CALL add_column_if_missing('collection_task', 'source_uri', 'VARCHAR(500)');
CALL add_column_if_missing('collection_task', 'dataset_id', 'BIGINT');
CALL add_column_if_missing('collection_task', 'label_schema_id', 'BIGINT');
CALL add_column_if_missing('collection_task', 'cleaning_config_json', 'TEXT');
CALL add_column_if_missing('collection_task', 'cleaning_summary_json', 'TEXT');
CALL add_column_if_missing('collection_task', 'annotation_rule_json', 'TEXT');
CALL add_column_if_missing('collection_task', 'collaboration_mode', "VARCHAR(20) DEFAULT 'SINGLE'");
CALL add_column_if_missing('collection_task', 'assigned_annotators', 'VARCHAR(500)');
CALL add_column_if_missing('collection_task', 'total_items', 'INT DEFAULT 0');
CALL add_column_if_missing('collection_task', 'completed_items', 'INT DEFAULT 0');

-- annotation_task: add 9 missing columns
CALL add_column_if_missing('annotation_task', 'description', 'VARCHAR(1000)');
CALL add_column_if_missing('annotation_task', 'assigned_annotators', 'INT');
CALL add_column_if_missing('annotation_task', 'quality_report_json', 'TEXT');
CALL add_column_if_missing('annotation_task', 'review_count', 'INT DEFAULT 0');
CALL add_column_if_missing('annotation_task', 'arbitration_count', 'INT DEFAULT 0');
CALL add_column_if_missing('annotation_task', 'pass_rate', 'DOUBLE');
CALL add_column_if_missing('annotation_task', 'consistency_rate', 'DOUBLE');
CALL add_column_if_missing('annotation_task', 'publish_version', 'VARCHAR(50)');
CALL add_column_if_missing('annotation_task', 'published_at', 'TIMESTAMP NULL');

-- annotation_item: add 10 missing columns
CALL add_column_if_missing('annotation_item', 'label_name', 'VARCHAR(200)');
CALL add_column_if_missing('annotation_item', 'comment', 'VARCHAR(1000)');
CALL add_column_if_missing('annotation_item', 'reviewed_by', 'BIGINT');
CALL add_column_if_missing('annotation_item', 'review_comment', 'VARCHAR(1000)');
CALL add_column_if_missing('annotation_item', 'annotated_at', 'TIMESTAMP NULL');
CALL add_column_if_missing('annotation_item', 'reviewed_at', 'TIMESTAMP NULL');
CALL add_column_if_missing('annotation_item', 'is_corrected', 'BOOLEAN DEFAULT FALSE');
CALL add_column_if_missing('annotation_item', 'original_confidence', 'DECIMAL(5,4)');
CALL add_column_if_missing('annotation_item', 'original_label_code', 'VARCHAR(100)');
CALL add_column_if_missing('annotation_item', 'corrected_at', 'TIMESTAMP NULL');

-- prediction_task: add 2 missing columns
CALL add_column_if_missing('prediction_task', 'task_type', "VARCHAR(20) DEFAULT 'REGRESSION'");
CALL add_column_if_missing('prediction_task', 'dl_model_id', 'VARCHAR(100)');

-- security_policy: add 4 missing columns
CALL add_column_if_missing('security_policy', 'rules', 'TEXT');
CALL add_column_if_missing('security_policy', 'description', 'VARCHAR(500)');
CALL add_column_if_missing('security_policy', 'priority', 'INT DEFAULT 0');
CALL add_column_if_missing('security_policy', 'is_enabled', 'BOOLEAN DEFAULT TRUE');

-- prediction_algorithm_config: add missing columns
CALL add_column_if_missing('prediction_algorithm_config', 'algorithm_family', 'VARCHAR(50)');
CALL add_column_if_missing('prediction_algorithm_config', 'task_type', 'VARCHAR(20)');
CALL add_column_if_missing('prediction_algorithm_config', 'default_params', 'TEXT');
CALL add_column_if_missing('prediction_algorithm_config', 'is_default', 'BOOLEAN DEFAULT FALSE');
CALL add_column_if_missing('prediction_algorithm_config', 'priority', 'INT DEFAULT 0');

DROP PROCEDURE IF EXISTS add_column_if_missing;
