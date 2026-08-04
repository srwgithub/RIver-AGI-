package com.river.agi.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.annotation.AuditOperation;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.dataset.service.LocalStorageService;
import com.river.agi.security.entity.*;
import com.river.agi.security.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {
    
    private final SensitiveDataDetectionMapper sensitiveDataDetectionMapper;
    private final AuditLogMapper auditLogMapper;
    private final SecurityScanTaskMapper securityScanTaskMapper;
    private final DatasetMapper datasetMapper;
    private final ObjectMapper objectMapper;
    private final DatasetDataReaderService dataReader;
    private final LocalStorageService localStorageService;
    private final ResourceAccessValidator accessValidator;
    private final SecurityUtils securityUtils;
    
    private static final String RULE_VERSION = "v2.1.0";

    private static final List<SensitiveRule> SENSITIVE_RULES = buildRules();

    private static List<SensitiveRule> buildRules() {
        List<SensitiveRule> rules = new ArrayList<>();
        rules.add(new SensitiveRule("身份证号", "HIGH", "FIELD_NAME_AND_CONTENT",
                safePattern("[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]"),
                safePattern("身份证号|身份证|idcard|id_number|id_no|ssn|idCardNo|idNumber"),
                new BigDecimal("0.99"), "建议进行加密存储或脱敏处理，严禁明文存储和传输",
                "数据主体：公民，涉及身份认证，泄露风险极高",
                "MASK_ID_CARD"));
        rules.add(new SensitiveRule("银行卡号", "HIGH", "FIELD_NAME_AND_CONTENT",
                safePattern("\\b[1-9]\\d{15,18}\\b"),
                safePattern("银行卡|卡号|bank_card|bank_card_number|card_no|account_no|bankAccount"),
                new BigDecimal("0.95"), "建议进行加密存储，展示时脱敏显示（如：6222****8888）",
                "数据主体：银行账户，涉及资金安全，泄露风险极高",
                "MASK_BANK_CARD"));
        rules.add(new SensitiveRule("手机号", "MEDIUM", "FIELD_NAME_AND_CONTENT",
                safePattern("\\b1[3-9]\\d{9}\\b"),
                safePattern("手机号|手机|phone|mobile|cellphone|tel|phoneNumber|mobilePhone"),
                new BigDecimal("0.90"), "建议脱敏显示（如：138****8888），禁止明文导出",
                "数据主体：个人联系方式，存在骚扰和诈骗风险",
                "MASK_PHONE"));
        rules.add(new SensitiveRule("邮箱地址", "MEDIUM", "FIELD_NAME_AND_CONTENT",
                safePattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
                safePattern("邮箱|email|e-mail|mail|emailAddress"),
                new BigDecimal("0.90"), "建议脱敏显示（如：zha***@example.com）",
                "数据主体：个人联系方式，存在钓鱼和垃圾邮件风险",
                "MASK_EMAIL"));
        rules.add(new SensitiveRule("密码", "HIGH", "FIELD_NAME_ONLY",
                null,
                safePattern("密码|password|pwd|passwd|secret|token|key|credential"),
                new BigDecimal("0.85"), "严禁存储明文密码，必须使用加密存储（如BCrypt）",
                "数据主体：系统凭证，涉及账户安全，泄露风险极高",
                "MASK_PASSWORD"));
        rules.add(new SensitiveRule("薪资信息", "HIGH", "FIELD_NAME_ONLY",
                null,
                safePattern("薪资|工资|收入|奖金|salary|wage|income|bonus|pay|compensation"),
                new BigDecimal("0.80"), "建议分级权限控制，仅HR和管理层可查看",
                "数据主体：员工薪酬，涉及隐私和合规要求",
                "MASK_SALARY"));
        rules.add(new SensitiveRule("地址", "MEDIUM", "FIELD_NAME_AND_CONTENT",
                safePattern("([\\u4e00-\\u9fa5]{2,}(省|市|区|县|街道|路|号|小区|栋|单元))"),
                safePattern("地址|住址|address|location|addr|homeAddress|residence"),
                new BigDecimal("0.75"), "建议移除详细地址，只保留城市级别",
                "数据主体：个人住址，存在安全风险",
                "MASK_ADDRESS"));
        rules.add(new SensitiveRule("姓名", "MEDIUM", "FIELD_NAME_AND_CONTENT",
                safePattern("[\\u4e00-\\u9fa5]{2,4}"),
                safePattern("姓名|名字|name|full_name|real_name|userName|employeeName"),
                new BigDecimal("0.70"), "建议使用匿名标识符替代真实姓名",
                "数据主体：个人身份，涉及隐私保护",
                "MASK_NAME"));
        rules.add(new SensitiveRule("证件号", "HIGH", "FIELD_NAME_AND_CONTENT",
                safePattern("\\b[A-Z0-9]{6,20}\\b"),
                safePattern("护照|驾照|passport|driver_license|license_no|passportNo|driverLicense"),
                new BigDecimal("0.85"), "建议加密存储，严格限制访问权限",
                "数据主体：证件信息，涉及身份认证和法律风险",
                "MASK_DOCUMENT_ID"));
        rules.add(new SensitiveRule("社保/公积金", "HIGH", "FIELD_NAME_ONLY",
                null,
                safePattern("社保|公积金|social_security|social_insurance|fund|insuranceNo"),
                new BigDecimal("0.80"), "建议加密存储，严格限制访问权限",
                "数据主体：社会保障信息，涉及合规和隐私要求",
                "MASK_SOCIAL_SECURITY"));
        return rules;
    }

    private static Pattern safePattern(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            log.warn("Invalid sensitive rule regex: {}, disabling rule", regex, e);
            return null;
        }
    }

    @AuditOperation(action = "SECURITY_SCAN", resourceType = "SECURITY_SCAN", description = "Scan dataset for sensitive data")
    @Transactional
    public Map<String, Object> scanSensitiveData(Long datasetId, Authentication authentication) {
        if (authentication != null) {
            accessValidator.validateDatasetOwnership(datasetId, securityUtils.getCurrentUserId(authentication));
        }
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        validateDatasetParsed(dataset);

        SecurityScanTask task = new SecurityScanTask();
        task.setDatasetId(datasetId);
        task.setStatus("RUNNING");
        task.setCreatedAt(LocalDateTime.now());
        task.setScanTime(LocalDateTime.now());
        securityScanTaskMapper.insert(task);

        int totalFieldsScanned = 0;
        int sensitiveFieldsFound = 0;
        List<Map<String, Object>> scanResults = new ArrayList<>();

        List<SensitiveDataDetection> detections = new ArrayList<>();

        try {
            Map<String, Object> schema = parseSchema(dataset.getSchemaJson());
            totalFieldsScanned = schema.size();

            List<Map<String, String>> rows;
            try {
                rows = dataReader.readRows(dataset);
            } catch (Exception ex) {
                log.warn("Failed to read dataset rows for security scan, continuing with field-only scan: {}", ex.getMessage());
                rows = new ArrayList<>();
            }

            for (Map.Entry<String, Object> entry : schema.entrySet()) {
                String fieldName = entry.getKey();
                if (fieldName == null || fieldName.isBlank()) continue;

                String fieldNameLower = fieldName.toLowerCase(Locale.ROOT);

                for (SensitiveRule rule : SENSITIVE_RULES) {
                    boolean fieldMatch = rule.fieldNamePattern() != null
                            && rule.fieldNamePattern().matcher(fieldNameLower).find();

                    int detectedCount = 0;
                    String sampleValue = null;

                    if ("FIELD_NAME_ONLY".equals(rule.matchType())) {
                        if (fieldMatch) {
                            detectedCount = rows.size();
                            if (!rows.isEmpty()) {
                                Map<String, String> firstRow = rows.get(0);
                                sampleValue = firstRow.get(fieldName);
                            }
                        }
                    } else if ("FIELD_NAME_AND_CONTENT".equals(rule.matchType()) || "CONTENT_ONLY".equals(rule.matchType())) {
                        boolean contentScan = "CONTENT_ONLY".equals(rule.matchType()) || fieldMatch;
                        if (contentScan && rule.contentPattern() != null) {
                            for (Map<String, String> row : rows) {
                                String value = row.get(fieldName);
                                if (value != null && !value.isBlank()
                                        && rule.contentPattern().matcher(value).find()) {
                                    detectedCount++;
                                    if (sampleValue == null) {
                                        sampleValue = value;
                                    }
                                }
                            }
                        }
                    }

                    if (detectedCount > 0) {
                        sensitiveFieldsFound++;

                        String maskedSample = maskValueByRule(sampleValue, rule.sensitiveType());

                        SensitiveDataDetection detection = new SensitiveDataDetection();
                        detection.setScanTaskId(task.getId());
                        detection.setColumnName(fieldName);
                        detection.setSensitiveType(rule.sensitiveType());
                        detection.setRiskLevel(rule.riskLevel());
                        detection.setDetectedCount(detectedCount);
                        detection.setSampleData(sampleValue);
                        detection.setMaskedSampleData(maskedSample);
                        detection.setConfidence(rule.confidence());
                        detection.setRuleVersion(RULE_VERSION);
                        detection.setSuggestion(rule.suggestion());
                        detection.setMatchType(rule.matchType());
                        detection.setRegexPattern(rule.contentPattern() != null ? rule.contentPattern().pattern() : "FIELD_NAME_MATCH");
                        detection.setTenantId(dataset.getTenantId() != null ? dataset.getTenantId() : 1L);
                        detection.setCreatedAt(LocalDateTime.now());

                        detections.add(detection);

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("fieldName", fieldName);
                        result.put("sensitiveType", rule.sensitiveType());
                        result.put("riskLevel", rule.riskLevel());
                        result.put("detectedCount", detectedCount);
                        result.put("sampleData", sampleValue);
                        result.put("maskedSampleData", maskedSample);
                        result.put("confidence", rule.confidence());
                        result.put("ruleVersion", RULE_VERSION);
                        result.put("suggestion", rule.suggestion());
                        scanResults.add(result);
                    }
                }
            }

            // 批量入库，避免逐条插入失败影响整体
            int inserted = 0;
            for (SensitiveDataDetection d : detections) {
                try {
                    sensitiveDataDetectionMapper.insert(d);
                    inserted++;
                } catch (Exception ex) {
                    log.warn("Failed to insert sensitive detection for field {}: {}", d.getColumnName(), ex.getMessage());
                }
            }
            log.info("Inserted {}/{} sensitive detections for dataset {}", inserted, detections.size(), datasetId);

            task.setRiskCount(sensitiveFieldsFound);
            task.setTotalFields(totalFieldsScanned);
            task.setSensitiveFieldsFound(sensitiveFieldsFound);

            long highRiskCount = countByRiskLevel(task.getId(), "HIGH");
            long mediumRiskCount = countByRiskLevel(task.getId(), "MEDIUM");
            long lowRiskCount = countByRiskLevel(task.getId(), "LOW");

            task.setHighRiskCount((int) highRiskCount);
            task.setMediumRiskCount((int) mediumRiskCount);
            task.setLowRiskCount((int) lowRiskCount);
            task.setStatus("COMPLETED");
            task.setScanTime(LocalDateTime.now());

            Map<String, Object> scanSummary = new LinkedHashMap<>();
            scanSummary.put("datasetId", datasetId);
            scanSummary.put("datasetName", dataset.getName());
            scanSummary.put("totalFields", totalFieldsScanned);
            scanSummary.put("sensitiveFieldsFound", sensitiveFieldsFound);
            scanSummary.put("highRiskCount", highRiskCount);
            scanSummary.put("mediumRiskCount", mediumRiskCount);
            scanSummary.put("lowRiskCount", lowRiskCount);
            scanSummary.put("scanResults", scanResults);
            task.setScanSummaryJson(objectMapper.writeValueAsString(scanSummary));

            log.info("Sensitive data scan completed for dataset {}: {} sensitive fields found", datasetId, sensitiveFieldsFound);

        } catch (Exception e) {
            log.error("Sensitive data scan failed for dataset {}, error: {}", datasetId, e.getMessage(), e);

            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setScanTime(LocalDateTime.now());

            AuditLog errorLog = new AuditLog();
            errorLog.setActionType("SECURITY_SCAN_FAILED");
            errorLog.setResourceType("SECURITY_SCAN");
            errorLog.setResourceId(task.getId());
            errorLog.setResourceName("Dataset: " + dataset.getName());

            Map<String, Object> errorDetails = new LinkedHashMap<>();
            errorDetails.put("datasetId", datasetId);
            errorDetails.put("errorMessage", e.getMessage());
            errorDetails.put("stackTrace", e.getClass().getName());
            try {
                errorLog.setOperationDetails(objectMapper.writeValueAsString(errorDetails));
            } catch (Exception ex) {
                errorLog.setOperationDetails("{\"error\":\"Failed to serialize error details\"}");
                log.warn("Failed to serialize error details for audit log", ex);
            }

            errorLog.setResult("FAILED");
            errorLog.setDurationMs(System.currentTimeMillis());
            errorLog.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(errorLog);

            securityScanTaskMapper.updateById(task);

            throw new BusinessException("安全扫描失败: " + e.getMessage());
        }

        securityScanTaskMapper.updateById(task);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", task.getId());
        response.put("status", task.getStatus());
        response.put("totalFieldsScanned", totalFieldsScanned);
        response.put("sensitiveFieldsFound", sensitiveFieldsFound);
        response.put("scanTime", task.getScanTime());
        response.put("ruleVersion", RULE_VERSION);
        response.put("highRiskCount", task.getHighRiskCount());
        response.put("mediumRiskCount", task.getMediumRiskCount());
        response.put("lowRiskCount", task.getLowRiskCount());
        response.put("scanResults", scanResults);

        return response;
    }

    private Map<String, Object> parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(schemaJson, new TypeReference<Object>() {});
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("Failed to parse dataset schema: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private long countByRiskLevel(Long scanTaskId, String riskLevel) {
        return sensitiveDataDetectionMapper.selectCount(
                new LambdaQueryWrapper<SensitiveDataDetection>()
                        .eq(SensitiveDataDetection::getScanTaskId, scanTaskId)
                        .eq(SensitiveDataDetection::getRiskLevel, riskLevel));
    }

    private String maskValueByRule(String value, String sensitiveType) {
        if (value == null) return null;
        return switch (sensitiveType) {
            case "身份证号" -> value.length() >= 10
                    ? value.substring(0, 4) + "********" + value.substring(value.length() - 4)
                    : "********";
            case "银行卡号" -> value.length() >= 8
                    ? value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4)
                    : "****";
            case "手机号" -> value.length() == 11
                    ? value.substring(0, 3) + "****" + value.substring(7)
                    : "****";
            case "邮箱地址" -> {
                int atIndex = value.indexOf('@');
                if (atIndex > 0) {
                    String prefix = value.substring(0, Math.min(3, atIndex));
                    String suffix = value.substring(atIndex);
                    yield prefix + "***" + suffix;
                }
                yield "***";
            }
            case "地址" -> value.length() > 6 ? value.substring(0, 3) + "***" : "***";
            case "姓名" -> value.length() > 1 ? value.charAt(0) + "**" : "**";
            default -> "***";
        };
    }

    public List<SensitiveDataDetection> getSensitiveDetections(Long scanTaskId) {
        return sensitiveDataDetectionMapper.selectByScanTaskId(scanTaskId);
    }

    public List<Map<String, Object>> getSensitiveDetectionsByDataset(Long datasetId, Authentication authentication) {
        accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(authentication));
        SecurityScanTask latestTask = securityScanTaskMapper.selectOne(
                new LambdaQueryWrapper<SecurityScanTask>()
                        .eq(SecurityScanTask::getDatasetId, datasetId)
                        .eq(SecurityScanTask::getStatus, "COMPLETED")
                        .orderByDesc(SecurityScanTask::getCreatedAt)
                        .last("LIMIT 1")
        );

        if (latestTask == null) {
            return new ArrayList<>();
        }

        List<SensitiveDataDetection> detections = sensitiveDataDetectionMapper.selectByScanTaskId(latestTask.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (SensitiveDataDetection detection : detections) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", detection.getId());
            item.put("fieldName", detection.getColumnName());
            item.put("sensitiveType", detection.getSensitiveType());
            item.put("riskLevel", detection.getRiskLevel());
            item.put("confidence", detection.getConfidence());
            item.put("suggestion", detection.getSuggestion());
            item.put("detectedCount", detection.getDetectedCount());
            item.put("sampleData", detection.getSampleData());
            item.put("maskedSampleData", detection.getMaskedSampleData());
            item.put("ruleVersion", detection.getRuleVersion());
            item.put("matchType", detection.getMatchType());
            result.add(item);
        }

        return result;
    }

    public Map<String, Object> getScanResults(Long datasetId) {
        Map<String, Object> result = new LinkedHashMap<>();

        SecurityScanTask latestTask = securityScanTaskMapper.selectOne(
                new LambdaQueryWrapper<SecurityScanTask>()
                        .eq(SecurityScanTask::getDatasetId, datasetId)
                        .eq(SecurityScanTask::getStatus, "COMPLETED")
                        .orderByDesc(SecurityScanTask::getCreatedAt)
                        .last("LIMIT 1")
        );

        if (latestTask == null) {
            result.put("status", "NO_SCAN");
            result.put("message", "No security scan has been completed for this dataset");
            result.put("scanResults", new ArrayList<>());
            return result;
        }

        result.put("status", latestTask.getStatus());
        result.put("scanTaskId", latestTask.getId());
        result.put("scanTime", latestTask.getCreatedAt());
        result.put("totalFieldsScanned", latestTask.getTotalFields());
        result.put("sensitiveFieldsFound", latestTask.getSensitiveFieldsFound());
        result.put("highRiskCount", latestTask.getHighRiskCount());
        result.put("mediumRiskCount", latestTask.getMediumRiskCount());
        result.put("lowRiskCount", latestTask.getLowRiskCount());

        List<SensitiveDataDetection> detections = sensitiveDataDetectionMapper.selectByScanTaskId(latestTask.getId());
        List<Map<String, Object>> scanResults = new ArrayList<>();
        for (SensitiveDataDetection detection : detections) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fieldName", detection.getColumnName());
            item.put("sensitiveType", detection.getSensitiveType());
            item.put("riskLevel", detection.getRiskLevel());
            item.put("confidence", detection.getConfidence());
            item.put("detectedCount", detection.getDetectedCount());
            item.put("sampleData", detection.getSampleData());
            item.put("maskedSampleData", detection.getMaskedSampleData());
            item.put("ruleVersion", detection.getRuleVersion());
            item.put("suggestion", detection.getSuggestion());
            scanResults.add(item);
        }
        result.put("scanResults", scanResults);

        return result;
    }

    public SecurityScanTask getScanTask(Long taskId) {
        SecurityScanTask task = securityScanTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Security scan task not found");
        }
        return task;
    }

    public void logAction(String actionType, String resourceType, Long resourceId,
                          String resourceName, String operationDetails, String result,
                          Authentication authentication, HttpServletRequest request) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActionType(actionType);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId);
        auditLog.setResourceName(resourceName);

        if (authentication != null && authentication.isAuthenticated()) {
            auditLog.setUsername(authentication.getName());
        }

        auditLog.setIpAddress(getClientIp(request));
        auditLog.setUserAgent(request.getHeader("User-Agent"));
        auditLog.setOperationDetails(operationDetails);
        auditLog.setResult(result);
        auditLog.setCreatedAt(LocalDateTime.now());

        auditLogMapper.insert(auditLog);
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

    public PageResult<AuditLog> getAuditLogs(int page, int size, Long userId, String resourceType) {
        return getAuditLogs(page, size, userId, resourceType, null, null, null);
    }

    public PageResult<AuditLog> getAuditLogs(int page, int size, Long userId, String resourceType,
                                             String actionType, java.time.LocalDate startDate,
                                             java.time.LocalDate endDate) {
        Page<AuditLog> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getCreatedAt);
        if (userId != null) query.eq(AuditLog::getUserId, userId);
        if (resourceType != null && !resourceType.isBlank()) query.like(AuditLog::getResourceType, resourceType);
        if (actionType != null && !actionType.isBlank()) query.like(AuditLog::getActionType, actionType);
        if (startDate != null) query.ge(AuditLog::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) query.lt(AuditLog::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        Page<AuditLog> pageResult = auditLogMapper.selectPage(pageRequest, query);

        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }

    public Map<String, Object> getComplianceSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        long auditLogs = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>());
        long highRisk = sensitiveDataDetectionMapper.selectCount(new LambdaQueryWrapper<SensitiveDataDetection>()
                .eq(SensitiveDataDetection::getRiskLevel, "HIGH"));
        long scans = securityScanTaskMapper.selectCount(new LambdaQueryWrapper<SecurityScanTask>());
        summary.put("auditLogs", auditLogs);
        summary.put("highRiskCount", highRisk);
        summary.put("securityScans", scans);
        summary.put("auditTrailComplete", auditLogs > 0);
        summary.put("privacyProtectionConfigured", highRisk == 0 || scans > 0);
        summary.put("standards", List.of("《数据安全法》", "《个人信息保护法》"));
        summary.put("generatedAt", LocalDateTime.now());
        return summary;
    }

    @AuditOperation(action = "MASK_DATA", resourceType = "SECURITY_MASK", description = "Mask sensitive data in dataset with preview and file generation")
    public Map<String, Object> maskSensitiveData(Long datasetId, Map<String, Object> maskRequest, Authentication authentication) {
        if (authentication != null) {
            accessValidator.validateDatasetOwnership(datasetId, securityUtils.getCurrentUserId(authentication));
        }
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        List<String> fieldsToMask = (List<String>) maskRequest.get("fields");
        String maskType = (String) maskRequest.getOrDefault("maskType", "partial");
        List<Map<String, Object>> fieldRules = (List<Map<String, Object>>) maskRequest.get("fieldRules");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", datasetId);
        result.put("maskedFields", fieldsToMask);
        result.put("maskType", maskType);
        result.put("status", "COMPLETED");
        result.put("ruleVersion", RULE_VERSION);

        List<Map<String, Object>> maskedPreview = new ArrayList<>();
        try {
            List<Map<String, String>> rows = dataReader.readRows(dataset);
            if (rows != null && !rows.isEmpty()) {
                List<String> headers = new ArrayList<>(rows.get(0).keySet());

                int previewLimit = Math.min(5, rows.size());
                StringBuilder csvContent = new StringBuilder();

                csvContent.append(String.join(",", headers)).append("\n");

                Map<String, String> maskRules = new HashMap<>();
                if (fieldRules != null) {
                    for (Map<String, Object> rule : fieldRules) {
                        String fieldName = (String) rule.get("fieldName");
                        String ruleMaskType = (String) rule.getOrDefault("maskType", "partial");
                        maskRules.put(fieldName, ruleMaskType);
                    }
                }

                for (int i = 0; i < rows.size(); i++) {
                    Map<String, String> row = rows.get(i);
                    List<String> maskedRow = new ArrayList<>();

                    for (String header : headers) {
                        String value = row.getOrDefault(header, "");
                        String effectiveMaskType = maskRules.getOrDefault(header, maskType);
                        maskedRow.add(maskValueWithHeader(value, effectiveMaskType, header));
                    }

                    csvContent.append(String.join(",", maskedRow)).append("\n");

                    if (i < previewLimit) {
                        Map<String, Object> previewRow = new LinkedHashMap<>();
                        for (int j = 0; j < headers.size(); j++) {
                            previewRow.put(headers.get(j), j < maskedRow.size() ? maskedRow.get(j) : "");
                        }
                        maskedPreview.add(previewRow);
                    }
                }

                String maskedFilename = localStorageService.writeFile(
                        csvContent.toString().getBytes(),
                        "masked_" + dataset.getName() + ".csv"
                );

                result.put("maskedFileUrl", "/api/v1/files/" + maskedFilename);
                result.put("maskedPreview", maskedPreview);
                result.put("totalRowsMasked", rows.size());
                result.put("totalFieldsMasked", headers.size());

                log.info("Masked {} fields for dataset {}, generated masked file: {}",
                        fieldsToMask != null ? fieldsToMask.size() : headers.size(),
                        datasetId, maskedFilename);
            }
        } catch (Exception e) {
            log.error("Failed to generate masked data for dataset {}", datasetId, e);
            result.put("error", "Masking failed: " + e.getMessage());
        }

        return result;
    }

    private String maskValueWithHeader(String value, String maskType, String fieldName) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        for (SensitiveRule rule : SENSITIVE_RULES) {
            if (rule.fieldNamePattern() != null && rule.fieldNamePattern().matcher(fieldName).find()) {
                return applyMask(value, maskType, rule);
            }
            if (rule.contentPattern() != null && rule.contentPattern().matcher(value).find()) {
                return applyMask(value, maskType, rule);
            }
        }

        if (isSensitiveHeader(fieldName)) {
            return applyMask(value, maskType, null);
        }

        return value;
    }

    private String applyMask(String value, String maskType, SensitiveRule rule) {
        if ("complete".equals(maskType)) {
            return "****";
        } else if ("hash".equals(maskType)) {
            return "***HASH***";
        } else if ("email".equals(maskType)) {
            int atIndex = value.indexOf('@');
            if (atIndex > 1) {
                return value.substring(0, 2) + "***" + value.substring(atIndex);
            }
            return "***@***";
        } else {
            if (rule != null && "MASK_PHONE".equals(rule.maskType())) {
                if (value.length() >= 7) {
                    return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
                }
                return "****";
            } else if (rule != null && "MASK_ID_CARD".equals(rule.maskType())) {
                if (value.length() >= 14) {
                    return value.substring(0, 4) + "*********" + value.substring(value.length() - 4);
                }
                return "****";
            } else if (rule != null && "MASK_BANK_CARD".equals(rule.maskType())) {
                if (value.length() >= 8) {
                    return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
                }
                return "****";
            } else if (rule != null && "MASK_NAME".equals(rule.maskType())) {
                if (value.length() > 1) {
                    return value.charAt(0) + "*";
                }
                return "*";
            }

            if (value.length() > 4) {
                return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
            }
            return "****";
        }
    }

    private boolean isSensitiveHeader(String fieldName) {
        String lowerName = fieldName.toLowerCase(Locale.ROOT);
        return lowerName.contains("password") || lowerName.contains("secret") ||
               lowerName.contains("token") || lowerName.contains("key") ||
               lowerName.contains("salary") || lowerName.contains("income") ||
               lowerName.contains("revenue") || lowerName.contains("pwd") ||
               lowerName.contains("credential");
    }

    public Map<String, Object> getSecurityDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        long totalScans = securityScanTaskMapper.selectCount(new LambdaQueryWrapper<SecurityScanTask>());
        dashboard.put("totalScans", totalScans);

        long completedScans = securityScanTaskMapper.selectCount(
                new LambdaQueryWrapper<SecurityScanTask>().eq(SecurityScanTask::getStatus, "COMPLETED"));
        dashboard.put("completedScans", completedScans);

        long totalRisks = sensitiveDataDetectionMapper.selectCount(new LambdaQueryWrapper<SensitiveDataDetection>());
        dashboard.put("totalRisks", totalRisks);

        long highRiskCount = sensitiveDataDetectionMapper.selectCount(
                new LambdaQueryWrapper<SensitiveDataDetection>().eq(SensitiveDataDetection::getRiskLevel, "HIGH"));
        dashboard.put("highRiskCount", highRiskCount);

        long mediumRiskCount = sensitiveDataDetectionMapper.selectCount(
                new LambdaQueryWrapper<SensitiveDataDetection>().eq(SensitiveDataDetection::getRiskLevel, "MEDIUM"));
        dashboard.put("mediumRiskCount", mediumRiskCount);

        long lowRiskCount = sensitiveDataDetectionMapper.selectCount(
                new LambdaQueryWrapper<SensitiveDataDetection>().eq(SensitiveDataDetection::getRiskLevel, "LOW"));
        dashboard.put("lowRiskCount", lowRiskCount);

        long totalAuditLogs = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>());
        dashboard.put("totalAuditLogs", totalAuditLogs);

        return dashboard;
    }

    public long getScanTaskCount() {
        return securityScanTaskMapper.selectCount(new LambdaQueryWrapper<>());
    }

    private void validateDatasetParsed(Dataset dataset) {
        String status = dataset.getStatus();
        if (!"PARSED".equals(status)) {
            throw new BusinessException("数据集尚未解析完成 (当前状态: " + status + ")，请先执行解析后再进行安全扫描");
        }
        if (dataset.getSchemaJson() == null || dataset.getSchemaJson().isBlank()) {
            throw new BusinessException("数据集 Schema 为空，无法执行安全扫描");
        }
        if (dataset.getRowCount() == null || dataset.getRowCount() <= 0) {
            throw new BusinessException("数据集行数为 0，无法执行安全扫描");
        }
    }

    private record SensitiveRule(String sensitiveType, String riskLevel, String matchType,
                                  Pattern contentPattern, Pattern fieldNamePattern,
                                  BigDecimal confidence, String suggestion,
                                  String impactDescription, String maskType) {
    }
}
