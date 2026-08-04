package com.river.agi.privacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 隐私政策知情同意记录（合同 14.2.1 知情同意）。
 * 每次用户同意隐私政策时落库一条记录，留存同意时间、政策版本与 IP。
 */
@Data
@TableName("privacy_consent")
public class PrivacyConsent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 同意的隐私政策版本号 */
    private String policyVersion;

    /** 同意方式：REGISTER / EXPLICIT / IMPLICIT */
    private String consentType;

    /** 用户 IP 地址 */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    private Long tenantId;

    private LocalDateTime consentAt;

    private LocalDateTime createdAt;
}
