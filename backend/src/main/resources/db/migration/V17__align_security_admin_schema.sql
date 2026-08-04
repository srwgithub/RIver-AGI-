-- Align legacy authorization tables with the current security-admin entities.
-- MySQL-compatible, idempotent migration for databases created by older init.sql files.

SET @db_name = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_role' AND column_name='tenant_id')=0,
    'ALTER TABLE sys_role ADD COLUMN tenant_id BIGINT DEFAULT 1', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_role' AND column_name='created_by')=0,
    'ALTER TABLE sys_role ADD COLUMN created_by BIGINT NULL', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_role' AND column_name='updated_at')=0,
    'ALTER TABLE sys_role ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_role' AND column_name='deleted')=0,
    'ALTER TABLE sys_role ADD COLUMN deleted INT DEFAULT 0', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='tenant_id')=0,
    'ALTER TABLE sys_permission ADD COLUMN tenant_id BIGINT DEFAULT 1', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='resource_type')=0,
    'ALTER TABLE sys_permission ADD COLUMN resource_type VARCHAR(50) NULL', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='resource_path')=0,
    'ALTER TABLE sys_permission ADD COLUMN resource_path VARCHAR(200) NULL', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='sort_order')=0,
    'ALTER TABLE sys_permission ADD COLUMN sort_order INT DEFAULT 0', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='parent_id')=0,
    'ALTER TABLE sys_permission ADD COLUMN parent_id BIGINT NULL', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='created_by')=0,
    'ALTER TABLE sys_permission ADD COLUMN created_by BIGINT NULL', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='updated_at')=0,
    'ALTER TABLE sys_permission ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='sys_permission' AND column_name='deleted')=0,
    'ALTER TABLE sys_permission ADD COLUMN deleted INT DEFAULT 0', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='security_policy' AND column_name='tenant_id')=0,
    'ALTER TABLE security_policy ADD COLUMN tenant_id BIGINT DEFAULT 1', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='security_policy' AND column_name='classification')=0,
    'ALTER TABLE security_policy ADD COLUMN classification VARCHAR(30) DEFAULT ''INTERNAL''', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='security_policy' AND column_name='rules_json')=0,
    'ALTER TABLE security_policy ADD COLUMN rules_json TEXT NULL', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='security_policy' AND column_name='enabled')=0,
    'ALTER TABLE security_policy ADD COLUMN enabled BOOLEAN DEFAULT TRUE', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db_name AND table_name='security_policy' AND column_name='updated_at')=0,
    'ALTER TABLE security_policy ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP', 'SELECT 1'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE sys_role SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_permission SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE security_policy SET tenant_id = 1 WHERE tenant_id IS NULL;
