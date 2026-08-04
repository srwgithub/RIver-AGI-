-- 合同 14.1.3 数据备份：增加完整性校验与异地备份字段
ALTER TABLE backup_record ADD COLUMN IF NOT EXISTS checksum VARCHAR(128) NULL;
ALTER TABLE backup_record ADD COLUMN IF NOT EXISTS offsite_path VARCHAR(500) NULL;

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
CREATE INDEX IF NOT EXISTS idx_privacy_consent_user_id ON privacy_consent (user_id);
CREATE INDEX IF NOT EXISTS idx_privacy_consent_policy_version ON privacy_consent (policy_version);
