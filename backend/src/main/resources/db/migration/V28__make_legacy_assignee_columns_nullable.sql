-- Keep installations created from the legacy schema writable after the
-- annotation assignee entity moved from user_id/role to annotator_id.
SET @user_id_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'annotation_task_assignee'
      AND column_name = 'user_id'
);
SET @make_user_id_nullable_sql := IF(
    @user_id_exists = 1,
    'ALTER TABLE annotation_task_assignee MODIFY COLUMN user_id BIGINT NULL',
    'SELECT 1'
);
PREPARE make_user_id_nullable_stmt FROM @make_user_id_nullable_sql;
EXECUTE make_user_id_nullable_stmt;
DEALLOCATE PREPARE make_user_id_nullable_stmt;

SET @role_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'annotation_task_assignee'
      AND column_name = 'role'
);
SET @make_role_nullable_sql := IF(
    @role_exists = 1,
    'ALTER TABLE annotation_task_assignee MODIFY COLUMN role VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE make_role_nullable_stmt FROM @make_role_nullable_sql;
EXECUTE make_role_nullable_stmt;
DEALLOCATE PREPARE make_role_nullable_stmt;

-- Backfill the legacy column when present so old readers retain the assignment.
UPDATE annotation_task_assignee
SET user_id = annotator_id
WHERE user_id IS NULL AND annotator_id IS NOT NULL;
