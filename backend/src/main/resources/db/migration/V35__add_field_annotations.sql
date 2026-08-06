-- Persist field-level multi-label annotations without breaking the legacy row label.
-- Use the project's MySQL-compatible conditional procedure because older MySQL
-- versions reject ALTER TABLE ... ADD COLUMN IF NOT EXISTS.
DELIMITER //
DROP PROCEDURE IF EXISTS add_field_annotations_column//
CREATE PROCEDURE add_field_annotations_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'annotation_item'
          AND column_name = 'field_annotations_json'
    ) THEN
        ALTER TABLE annotation_item ADD COLUMN field_annotations_json TEXT NULL;
    END IF;
END//
DELIMITER ;
CALL add_field_annotations_column();
DROP PROCEDURE IF EXISTS add_field_annotations_column;
