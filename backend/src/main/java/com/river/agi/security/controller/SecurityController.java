package com.river.agi.security.controller;

import com.river.agi.security.entity.AuditLog;
import com.river.agi.security.entity.SensitiveDataDetection;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.service.SecurityService;
import com.river.agi.common.ApiResponse;
import com.river.agi.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security", description = "Security and audit APIs")
public class SecurityController {
    
    private final SecurityService securityService;
    
    @PostMapping("/datasets/{id}/scan")
    @Operation(summary = "Scan dataset", description = "Scan dataset for sensitive information")
    public ApiResponse<Map<String, Object>> scanDataset(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        return ApiResponse.ok(securityService.scanSensitiveData(id, authentication));
    }
    
    @GetMapping("/datasets/{id}/risks")
    @Operation(summary = "Get dataset risks", description = "Get security risks for a dataset")
    public ApiResponse<List<Map<String, Object>>> getDatasetRisks(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            Authentication authentication) {
        return ApiResponse.ok(securityService.getSensitiveDetectionsByDataset(id, authentication));
    }
    
    @PostMapping("/datasets/{id}/mask")
    @Operation(summary = "Mask dataset", description = "Mask sensitive data in a dataset")
    public ApiResponse<Map<String, Object>> maskDataset(
            @Parameter(description = "Dataset ID") @PathVariable Long id,
            @RequestBody Map<String, Object> maskRequest,
            Authentication authentication) {
        return ApiResponse.ok(securityService.maskSensitiveData(id, maskRequest, authentication));
    }
    
    @GetMapping("/dashboard")
    @Operation(summary = "Security dashboard", description = "Get security dashboard statistics")
    public ApiResponse<Map<String, Object>> getSecurityDashboard() {
        return ApiResponse.ok(securityService.getSecurityDashboard());
    }
    
    @GetMapping("/scans/count")
    @Operation(summary = "Count scan tasks", description = "Get total number of security scan tasks")
    public ApiResponse<Long> countScanTasks() {
        return ApiResponse.ok(securityService.getScanTaskCount());
    }
}
