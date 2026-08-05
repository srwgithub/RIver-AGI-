-- 合同 14.1.3 数据备份：增加完整性校验与异地备份字段
SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE backup_record ADD COLUMN checksum VARCHAR(128) NULL',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'backup_record' AND column_name = 'checksum');
PREPARE river_stmt FROM @sql;
EXECUTE river_stmt;
DEALLOCATE PREPARE river_stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE backup_record ADD COLUMN offsite_path VARCHAR(500) NULL',
    'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'backup_record' AND column_name = 'offsite_path');
PREPARE river_stmt FROM @sql;
EXECUTE river_stmt;
DEALLOCATE PREPARE river_stmt;

-- 合同 14.2.1 个人信息保护：隐私政策知情同意记录
CREATE TABLE IF NOT EXISTS privacy_consent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    policy_version VARCHAR(50) NOT NULL,
    consent_type VARCHAR(20),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    tenant_id BIGINT DEFAULT 1,
    consent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
SET @sql = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_privacy_consent_user_id ON privacy_consent (user_id)',
    'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'privacy_consent' AND index_name = 'idx_privacy_consent_user_id');
PREPARE river_stmt FROM @sql;
EXECUTE river_stmt;
DEALLOCATE PREPARE river_stmt;

SET @sql = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_privacy_consent_policy_version ON privacy_consent (policy_version)',
    'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'privacy_consent' AND index_name = 'idx_privacy_consent_policy_version');
PREPARE river_stmt FROM @sql;
EXECUTE river_stmt;
DEALLOCATE PREPARE river_stmt;
