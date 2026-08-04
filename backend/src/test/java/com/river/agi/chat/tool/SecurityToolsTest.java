package com.river.agi.chat.tool;

import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.security.service.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("安全工具测试")
class SecurityToolsTest {

    @Mock private SecurityService securityService;
    @Mock private ResourceAccessValidator accessValidator;
    @Mock private SecurityUtils securityUtils;
    @Mock private Authentication authentication;

    private SecurityTools tools;

    @BeforeEach
    void setUp() {
        tools = new SecurityTools(securityService, accessValidator, securityUtils);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        doNothing().when(accessValidator).validateDatasetAccess(anyLong(), anyLong());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("扫描敏感数据 - 成功返回结果")
    void scanSensitiveData_success() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("totalFieldsScanned", 5);
        result.put("sensitiveFieldsFound", 2);
        result.put("highRiskCount", 1);
        when(securityService.scanSensitiveData(eq(1L), any())).thenReturn(result);

        String json = tools.scanSensitiveData(1L);

        assertTrue(json.contains("COMPLETED"));
        assertTrue(json.contains("totalFieldsScanned"));
        assertTrue(json.contains("sensitiveFieldsFound"));
        verify(accessValidator).validateDatasetAccess(1L, 1L);
        verify(securityService).scanSensitiveData(eq(1L), any());
    }

    @Test
    @DisplayName("扫描敏感数据 - 含高风险字段")
    void scanSensitiveData_withHighRisk() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMPLETED");
        result.put("highRiskCount", 3);
        result.put("mediumRiskCount", 2);
        result.put("lowRiskCount", 1);
        when(securityService.scanSensitiveData(eq(2L), any())).thenReturn(result);

        String json = tools.scanSensitiveData(2L);

        assertTrue(json.contains("highRiskCount"));
        assertTrue(json.contains("3"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 异常返回错误 JSON")
    void scanSensitiveData_exception() {
        when(securityService.scanSensitiveData(anyLong(), any())).thenThrow(new RuntimeException("scan failed"));

        String json = tools.scanSensitiveData(1L);

        assertTrue(json.contains("\"error\""));
        assertTrue(json.contains("scan failed"));
    }

    @Test
    @DisplayName("扫描敏感数据 - 访问校验失败返回错误")
    void scanSensitiveData_accessDenied() {
        doThrow(new RuntimeException("access denied")).when(accessValidator).validateDatasetAccess(1L, 1L);

        String json = tools.scanSensitiveData(1L);

        assertTrue(json.contains("\"error\""));
        assertTrue(json.contains("access denied"));
    }

    @Test
    @DisplayName("扫描敏感数据 - datasetId 为 null 触发异常路径")
    void scanSensitiveData_nullDatasetId() {
        when(securityService.scanSensitiveData(isNull(), any())).thenThrow(new NullPointerException("null id"));

        String json = tools.scanSensitiveData(null);

        assertTrue(json.contains("\"error\""));
    }

    @Test
    @DisplayName("获取扫描结果 - 成功返回结果")
    void getSecurityScanResults_success() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", 1L);
        result.put("detections", java.util.List.of());
        result.put("totalDetections", 0);
        when(securityService.getScanResults(1L)).thenReturn(result);

        String json = tools.getSecurityScanResults(1L);

        assertTrue(json.contains("datasetId"));
        assertTrue(json.contains("detections"));
        verify(accessValidator).validateDatasetAccess(1L, 1L);
        verify(securityService).getScanResults(1L);
    }

    @Test
    @DisplayName("获取扫描结果 - 含检测项详情")
    void getSecurityScanResults_withDetections() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", 1L);
        result.put("totalDetections", 2);
        result.put("detections", java.util.List.of(
                Map.of("fieldName", "phone", "riskLevel", "HIGH"),
                Map.of("fieldName", "email", "riskLevel", "MEDIUM")
        ));
        when(securityService.getScanResults(1L)).thenReturn(result);

        String json = tools.getSecurityScanResults(1L);

        assertTrue(json.contains("phone"));
        assertTrue(json.contains("email"));
        assertTrue(json.contains("HIGH"));
    }

    @Test
    @DisplayName("获取扫描结果 - 异常返回错误 JSON")
    void getSecurityScanResults_exception() {
        when(securityService.getScanResults(anyLong())).thenThrow(new RuntimeException("not found"));

        String json = tools.getSecurityScanResults(1L);

        assertTrue(json.contains("\"error\""));
        assertTrue(json.contains("not found"));
    }

    @Test
    @DisplayName("获取扫描结果 - 访问校验失败返回错误")
    void getSecurityScanResults_accessDenied() {
        doThrow(new RuntimeException("forbidden")).when(accessValidator).validateDatasetAccess(1L, 1L);

        String json = tools.getSecurityScanResults(1L);

        assertTrue(json.contains("\"error\""));
        assertTrue(json.contains("forbidden"));
    }
}
