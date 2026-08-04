SET @prediction_error_message_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'prediction_task'
      AND column_name = 'error_message'
);
SET @prediction_error_message_sql = IF(
    @prediction_error_message_exists = 0,
    'ALTER TABLE prediction_task ADD COLUMN error_message TEXT NULL AFTER status',
    'SELECT 1'
);
PREPARE prediction_error_message_stmt FROM @prediction_error_message_sql;
EXECUTE prediction_error_message_stmt;
DEALLOCATE PREPARE prediction_error_message_stmt;
