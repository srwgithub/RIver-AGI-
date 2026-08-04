package com.river.agi.privacy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.river.agi.auth.entity.User;
import com.river.agi.auth.mapper.UserMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.annotation.AuditOperation;
import com.river.agi.privacy.entity.PrivacyConsent;
import com.river.agi.privacy.mapper.PrivacyConsentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 隐私政策与数据主体权利服务（合同 14.2 个人信息保护 / 14.3 数据合规）。
 * 提供隐私政策查询、知情同意记录、数据留存策略与数据主体权利（删除/导出）能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyService {

    private static final String POLICY_VERSION = "v1.0-2026";
    private static final int DEFAULT_RETENTION_DAYS = 365;

    private final PrivacyConsentMapper privacyConsentMapper;
    private final UserMapper userMapper;

    /**
     * 获取隐私政策内容（合同 14.2.1 知情同意）。
     */
    public Map<String, Object> getPrivacyPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("version", POLICY_VERSION);
        policy.put("title", "RIver AGI 系统隐私政策");
        policy.put("effectiveDate", "2026-01-01");
        policy.put("applicableLaws", List.of("《中华人民共和国个人信息保护法》", "《中华人民共和国数据安全法》", "《中华人民共和国网络安全法》"));
        policy.put("sections", buildPolicySections());
        policy.put("dataRetentionDays", DEFAULT_RETENTION_DAYS);
        policy.put("dataSubjectRights", List.of(
                "知情权：了解个人信息处理目的、方式、范围",
                "决定权：同意或撤回个人信息处理",
                "查询权：查询本人个人信息",
                "更正权：更正不准确个人信息",
                "删除权：请求删除个人信息",
                "可携带权：导出本人个人信息",
                "投诉权：向监管部门投诉"
        ));
        return policy;
    }

    private List<Map<String, Object>> buildPolicySections() {
        List<Map<String, Object>> sections = new java.util.ArrayList<>();

        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("title", "一、信息收集范围");
        s1.put("content", "系统收集用户账户信息（用户名、邮箱、手机号、真实姓名）、操作审计日志、数据集内容。敏感个人信息（身份证、银行卡等）经识别后自动加密存储与脱敏展示。");
        sections.add(s1);

        Map<String, Object> s2 = new LinkedHashMap<>();
        s2.put("title", "二、信息使用目的");
        s2.put("content", "收集的信息仅用于：1)账户身份认证与权限控制；2)数据分析与预测任务执行；3)安全审计与合规追溯；4)系统运维与故障排查。不用于除上述目的外的其他用途。");
        sections.add(s2);

        Map<String, Object> s3 = new LinkedHashMap<>();
        s3.put("title", "三、信息存储与保护");
        s3.put("content", "个人信息采用 AES/GCM 加密存储，访问受 RBAC 权限控制，所有访问行为记入审计日志。系统每日自动备份，备份数据加密留存 " + DEFAULT_RETENTION_DAYS + " 天。");
        sections.add(s3);

        Map<String, Object> s4 = new LinkedHashMap<>();
        s4.put("title", "四、信息共享与披露");
        s4.put("content", "未经用户同意，不向第三方共享个人信息。法律法规要求或监管部门依法要求时，在最小必要范围内披露。");
        sections.add(s4);

        Map<String, Object> s5 = new LinkedHashMap<>();
        s5.put("title", "五、数据主体权利");
        s5.put("content", "用户享有知情、决定、查询、更正、删除、导出、投诉等权利。可通过系统接口或联系数据保护官行使权利，系统在 15 个工作日内响应。");
        sections.add(s5);

        Map<String, Object> s6 = new LinkedHashMap<>();
        s6.put("title", "六、数据留存与删除");
        s6.put("content", "用户个人信息留存期不超过 " + DEFAULT_RETENTION_DAYS + " 天，留存期满或用户注销后自动删除。审计日志按合规要求留存不少于 6 个月。");
        sections.add(s6);

        return sections;
    }

    /**
     * 记录用户知情同意（合同 14.2.1）。
     */
    @AuditOperation(action = "PRIVACY_CONSENT", resourceType = "PRIVACY", description = "Record user privacy consent")
    public PrivacyConsent recordConsent(Long userId, String username, String consentType,
                                        String ipAddress, String userAgent) {
        PrivacyConsent consent = new PrivacyConsent();
        consent.setUserId(userId);
        consent.setUsername(username);
        consent.setPolicyVersion(POLICY_VERSION);
        consent.setConsentType(consentType);
        consent.setIpAddress(ipAddress);
        consent.setUserAgent(userAgent);
        consent.setTenantId(1L);
        consent.setConsentAt(LocalDateTime.now());
        consent.setCreatedAt(LocalDateTime.now());
        privacyConsentMapper.insert(consent);
        log.info("Recorded privacy consent for user {} (version={}, type={})", userId, POLICY_VERSION, consentType);
        return consent;
    }

    /**
     * 查询用户同意历史。
     */
    public List<PrivacyConsent> getConsentHistory(Long userId) {
        return privacyConsentMapper.selectList(
                new LambdaQueryWrapper<PrivacyConsent>()
                        .eq(PrivacyConsent::getUserId, userId)
                        .orderByDesc(PrivacyConsent::getConsentAt));
    }

    /**
     * 验证用户是否已同意当前版本隐私政策。
     */
    public boolean hasCurrentConsent(Long userId) {
        Long count = privacyConsentMapper.selectCount(
                new LambdaQueryWrapper<PrivacyConsent>()
                        .eq(PrivacyConsent::getUserId, userId)
                        .eq(PrivacyConsent::getPolicyVersion, POLICY_VERSION));
        return count != null && count > 0;
    }

    /**
     * 数据主体权利-查询本人信息（合同 14.2.4）。
     */
    public Map<String, Object> exportPersonalData(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("realName", user.getRealName());
        data.put("createdAt", user.getCreatedAt());
        data.put("consentHistory", getConsentHistory(userId));
        data.put("exportedAt", LocalDateTime.now());
        data.put("retentionDays", DEFAULT_RETENTION_DAYS);
        return data;
    }

    /**
     * 数据主体权利-删除本人信息（合同 14.2.4 数据留存/删除策略）。
     * 逻辑删除用户个人可识别字段，保留审计必要的匿名化记录。
     */
    @AuditOperation(action = "PERSONAL_DATA_DELETE", resourceType = "PRIVACY", description = "Delete personal data per data subject request")
    @Transactional
    public Map<String, Object> deletePersonalData(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setEmail(null);
        user.setPhone(null);
        user.setRealName(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("status", "DELETED");
        result.put("message", "个人可识别信息已清除，审计记录匿名保留");
        result.put("deletedAt", LocalDateTime.now());
        log.info("Personal data deleted for user {} per data subject request", userId);
        return result;
    }

    /**
     * 数据留存策略信息（合同 14.2.4 / 14.3）。
     */
    public Map<String, Object> getRetentionPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("personalDataRetentionDays", DEFAULT_RETENTION_DAYS);
        policy.put("auditLogRetentionDays", 180);
        policy.put("backupRetentionDays", 30);
        policy.put("maxBackupCount", 10);
        policy.put("autoCleanupEnabled", true);
        policy.put("standards", List.of("《数据安全法》", "《个人信息保护法》"));
        return policy;
    }

    public String getPolicyVersion() {
        return POLICY_VERSION;
    }
}
