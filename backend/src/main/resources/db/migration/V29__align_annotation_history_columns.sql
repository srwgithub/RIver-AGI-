-- Align legacy annotation history installations with the current entity.
-- Keep the old change_type/note columns for backward compatibility.
SET @action_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_history' AND column_name = 'action'
);
SET @add_action_sql := IF(@action_exists = 0,
    'ALTER TABLE annotation_history ADD COLUMN action VARCHAR(50) NULL AFTER item_id', 'SELECT 1');
PREPARE add_action_stmt FROM @add_action_sql;
EXECUTE add_action_stmt;
DEALLOCATE PREPARE add_action_stmt;

SET @operator_id_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_history' AND column_name = 'operator_id'
);
SET @add_operator_id_sql := IF(@operator_id_exists = 0,
    'ALTER TABLE annotation_history ADD COLUMN operator_id BIGINT NULL', 'SELECT 1');
PREPARE add_operator_id_stmt FROM @add_operator_id_sql;
EXECUTE add_operator_id_stmt;
DEALLOCATE PREPARE add_operator_id_stmt;

SET @operator_name_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_history' AND column_name = 'operator_name'
);
SET @add_operator_name_sql := IF(@operator_name_exists = 0,
    'ALTER TABLE annotation_history ADD COLUMN operator_name VARCHAR(100) NULL', 'SELECT 1');
PREPARE add_operator_name_stmt FROM @add_operator_name_sql;
EXECUTE add_operator_name_stmt;
DEALLOCATE PREPARE add_operator_name_stmt;

SET @old_value_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_history' AND column_name = 'old_value'
);
SET @add_old_value_sql := IF(@old_value_exists = 0,
    'ALTER TABLE annotation_history ADD COLUMN old_value TEXT NULL', 'SELECT 1');
PREPARE add_old_value_stmt FROM @add_old_value_sql;
EXECUTE add_old_value_stmt;
DEALLOCATE PREPARE add_old_value_stmt;

SET @new_value_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_history' AND column_name = 'new_value'
);
SET @add_new_value_sql := IF(@new_value_exists = 0,
    'ALTER TABLE annotation_history ADD COLUMN new_value TEXT NULL', 'SELECT 1');
PREPARE add_new_value_stmt FROM @add_new_value_sql;
EXECUTE add_new_value_stmt;
DEALLOCATE PREPARE add_new_value_stmt;

SET @reason_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'annotation_history' AND column_name = 'reason'
);
SET @add_reason_sql := IF(@reason_exists = 0,
    'ALTER TABLE annotation_history ADD COLUMN reason VARCHAR(500) NULL', 'SELECT 1');
PREPARE add_reason_stmt FROM @add_reason_sql;
EXECUTE add_reason_stmt;
DEALLOCATE PREPARE add_reason_stmt;

-- Preserve the old history meaning for installations created with the legacy schema.
UPDATE annotation_history
SET action = change_type
WHERE action IS NULL AND change_type IS NOT NULL;
UPDATE annotation_history
SET reason = note
WHERE reason IS NULL AND note IS NOT NULL;
UPDATE annotation_history
SET old_value = old_label
WHERE old_value IS NULL AND old_label IS NOT NULL;
UPDATE annotation_history
SET new_value = new_label
WHERE new_value IS NULL AND new_label IS NOT NULL;
