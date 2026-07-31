package com.river.agi.chat.tool;

import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityTools {
    
    private final SecurityService securityService;
    private final ResourceAccessValidator accessValidator;
    private final SecurityUtils securityUtils;
    
    @Tool(description = "Scan a dataset for sensitive data including phone numbers, ID numbers, bank cards, emails, addresses, passwords and salary info. Returns found sensitive fields with risk levels, confidence scores and remediation suggestions. Use this when user asks about security risks or sensitive data.")
    public String scanSensitiveData(Long datasetId) {
        log.info("Tool called: scanSensitiveData, datasetId: {}", datasetId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validateDatasetAccess(datasetId, userId);
            
            var result = securityService.scanSensitiveData(datasetId, auth);
            return result.toString();
        } catch (Exception e) {
            log.error("Failed to scan sensitive data for dataset {}", datasetId, e);
            return "{\"error\": \"Failed to scan sensitive data: " + e.getMessage() + "\"}";
        }
    }
    
    @Tool(description = "Get security scan results for a previously scanned dataset. Returns detected sensitive data details including field names, types, hit counts, risk levels and remediation suggestions. Use this when user asks to view security findings.")
    public String getSecurityScanResults(Long datasetId) {
        log.info("Tool called: getSecurityScanResults, datasetId: {}", datasetId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validateDatasetAccess(datasetId, userId);
            
            var result = securityService.getScanResults(datasetId);
            return result.toString();
        } catch (Exception e) {
            log.error("Failed to get security scan results for dataset {}", datasetId, e);
            return "{\"error\": \"Failed to get scan results: " + e.getMessage() + "\"}";
        }
    }
}
