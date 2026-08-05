-- Keep the label schema table compatible with the entity used by the task configuration page.
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'label_schema'
      AND column_name = 'icon'
);
SET @add_icon_sql := IF(
    @column_exists = 0,
    'ALTER TABLE label_schema ADD COLUMN icon VARCHAR(100) NULL AFTER color',
    'SELECT 1'
);
PREPARE add_icon_stmt FROM @add_icon_sql;
EXECUTE add_icon_stmt;
DEALLOCATE PREPARE add_icon_stmt;
