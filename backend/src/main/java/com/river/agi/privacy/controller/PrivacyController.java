package com.river.agi.privacy.controller;

import com.river.agi.common.ApiResponse;
import com.river.agi.common.SecurityUtils;
import com.river.agi.privacy.entity.PrivacyConsent;
import com.river.agi.privacy.service.PrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 隐私政策与数据主体权利接口（合同 14.2 个人信息保护）。
 */
@RestController
@RequestMapping("/api/v1/privacy")
@RequiredArgsConstructor
@Tag(name = "Privacy", description = "Privacy policy and data subject rights APIs")
public class PrivacyController {

    private final PrivacyService privacyService;
    private final SecurityUtils securityUtils;

    @GetMapping("/policy")
    @Operation(summary = "Get privacy policy", description = "获取隐私政策内容（无需登录）")
    public ApiResponse<Map<String, Object>> getPrivacyPolicy() {
        return ApiResponse.ok(privacyService.getPrivacyPolicy());
    }

    @GetMapping("/retention")
    @Operation(summary = "Get data retention policy", description = "获取数据留存策略")
    public ApiResponse<Map<String, Object>> getRetentionPolicy() {
        return ApiResponse.ok(privacyService.getRetentionPolicy());
    }

    @PostMapping("/consent")
    @Operation(summary = "Record consent", description = "记录用户对隐私政策的知情同意")
    public ApiResponse<PrivacyConsent> recordConsent(Authentication authentication, HttpServletRequest request) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        String username = securityUtils.getCurrentUsername(authentication);
        String ip = getClientIp(request);
        String ua = request.getHeader("User-Agent");
        return ApiResponse.ok(privacyService.recordConsent(userId, username, "EXPLICIT", ip, ua));
    }

    @GetMapping("/consent/history")
    @Operation(summary = "Consent history", description = "查询当前用户的同意历史")
    public ApiResponse<List<PrivacyConsent>> consentHistory(Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        return ApiResponse.ok(privacyService.getConsentHistory(userId));
    }

    @GetMapping("/consent/status")
    @Operation(summary = "Consent status", description = "查询当前用户是否已同意最新隐私政策")
    public ApiResponse<Map<String, Object>> consentStatus(Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        boolean hasConsent = privacyService.hasCurrentConsent(userId);
        return ApiResponse.ok(Map.of(
                "userId", userId,
                "hasConsent", hasConsent,
                "policyVersion", privacyService.getPolicyVersion()
        ));
    }

    @GetMapping("/data/export")
    @Operation(summary = "Export personal data", description = "数据主体权利-导出本人个人信息")
    public ApiResponse<Map<String, Object>> exportPersonalData(Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        return ApiResponse.ok(privacyService.exportPersonalData(userId));
    }

    @DeleteMapping("/data")
    @Operation(summary = "Delete personal data", description = "数据主体权利-删除本人个人信息")
    public ApiResponse<Map<String, Object>> deletePersonalData(Authentication authentication) {
        Long userId = securityUtils.getCurrentUserId(authentication);
        return ApiResponse.ok(privacyService.deletePersonalData(userId));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
