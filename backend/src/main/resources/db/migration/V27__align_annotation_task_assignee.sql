-- Keep task assignment compatible with installations that still have the legacy user_id column.
SET @annotator_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'annotation_task_assignee'
      AND column_name = 'annotator_id'
);
SET @add_annotator_sql := IF(
    @annotator_exists = 0,
    'ALTER TABLE annotation_task_assignee ADD COLUMN annotator_id BIGINT NULL AFTER task_id',
    'SELECT 1'
);
PREPARE add_annotator_stmt FROM @add_annotator_sql;
EXECUTE add_annotator_stmt;
DEALLOCATE PREPARE add_annotator_stmt;

SET @assigned_by_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'annotation_task_assignee'
      AND column_name = 'assigned_by'
);
SET @add_assigned_by_sql := IF(
    @assigned_by_exists = 0,
    'ALTER TABLE annotation_task_assignee ADD COLUMN assigned_by BIGINT NULL AFTER annotator_id',
    'SELECT 1'
);
PREPARE add_assigned_by_stmt FROM @add_assigned_by_sql;
EXECUTE add_assigned_by_stmt;
DEALLOCATE PREPARE add_assigned_by_stmt;

-- Preserve existing assignments created by the legacy schema.
UPDATE annotation_task_assignee
SET annotator_id = user_id
WHERE annotator_id IS NULL AND user_id IS NOT NULL;
