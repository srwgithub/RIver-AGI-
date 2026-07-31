package com.river.agi.analysis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.analysis.entity.AnalysisTask;
import com.river.agi.analysis.entity.FieldStatistics;
import com.river.agi.analysis.entity.OutlierDetection;
import com.river.agi.analysis.mapper.AnalysisTaskMapper;
import com.river.agi.analysis.mapper.FieldStatisticsMapper;
import com.river.agi.analysis.mapper.OutlierDetectionMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.common.BusinessException;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.annotation.AuditOperation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {
    
    private final AnalysisTaskMapper analysisTaskMapper;
    private final FieldStatisticsMapper fieldStatisticsMapper;
    private final OutlierDetectionMapper outlierDetectionMapper;
    private final DatasetMapper datasetMapper;
    private final ObjectMapper objectMapper;
    private final DatasetDataReaderService dataReader;
    private final SecurityUtils securityUtils;
    private final ResourceAccessValidator accessValidator;
    
    private static final String QUALITY_RULE_VERSION = "v2.1.0";
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");
    
    @AuditOperation(action = "CREATE_TASK", resourceType = "ANALYSIS", description = "Create analysis task")
    public AnalysisTask createAnalysisTask(Long datasetId, String taskType, Map<String, Object> parameters) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        validateDatasetParsed(dataset);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            accessValidator.validateDatasetOwnership(datasetId, securityUtils.getCurrentUserId(auth));
        }

        AnalysisTask task = new AnalysisTask();
        task.setDatasetId(datasetId);
        task.setTaskType(taskType);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        analysisTaskMapper.insert(task);
        return task;
    }
    
    public AnalysisTask getAnalysisTask(Long taskId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("Analysis task not found");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            accessValidator.validateDatasetAccess(task.getDatasetId(), securityUtils.getCurrentUserId(auth));
        }
        return task;
    }

    public Page<AnalysisTask> listAnalysisTasks(Long datasetId, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = auth == null ? null : securityUtils.getCurrentUserId(auth);

        LambdaQueryWrapper<AnalysisTask> wrapper = new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getDeleted, 0)
                .eq(datasetId != null, AnalysisTask::getDatasetId, datasetId)
                .orderByDesc(AnalysisTask::getCreatedAt);

        // The current tenant model stores ownership on the dataset. Validate the
        // optional filter before querying, and constrain the result to datasets
        // the current user can access.
        if (datasetId != null && userId != null) {
            accessValidator.validateDatasetAccess(datasetId, userId);
        }

        Page<AnalysisTask> result = analysisTaskMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        if (datasetId == null && userId != null && !result.getRecords().isEmpty()) {
            result.setRecords(result.getRecords().stream()
                    .filter(task -> {
                        try {
                            accessValidator.validateDatasetAccess(task.getDatasetId(), userId);
                            return true;
                        } catch (RuntimeException ex) {
                            return false;
                        }
                    })
                    .toList());
        }
        return result;
    }
    
    @AuditOperation(action = "RUN_ANALYSIS", resourceType = "ANALYSIS", description = "Run data analysis")
    public AnalysisTask runAnalysis(Long datasetId, String taskType) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        validateDatasetParsed(dataset);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            accessValidator.validateDatasetOwnership(datasetId, securityUtils.getCurrentUserId(auth));
        }
        
        AnalysisTask task = new AnalysisTask();
        task.setDatasetId(datasetId);
        task.setTaskType(taskType);
        task.setStatus("RUNNING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.insert(task);
        
        try {
            Map<String, Object> results;
            switch (taskType.toUpperCase()) {
                case "PROFILE":
                    results = generateDataProfile(dataset, task.getId());
                    break;
                case "OUTLIERS":
                    results = detectOutliers(dataset, task.getId());
                    break;
                case "QUALITY":
                    results = analyzeDataQuality(dataset);
                    break;
                default:
                    throw new BusinessException("Unknown task type: " + taskType);
            }
            
            task.setResultJson(objectMapper.writeValueAsString(results));
            task.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Analysis failed", e);
            task.setStatus("FAILED");
            task.setResultJson("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
        
        task.setUpdatedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
        
        return task;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    
    private Map<String, Object> generateDataProfile(Dataset dataset, Long taskId) throws Exception {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("datasetId", dataset.getId());
        profile.put("rowCount", dataset.getRowCount());
        profile.put("columnCount", dataset.getColumnCount());
        
        Map<String, Object> schema = parseSchema(dataset.getSchemaJson());
        profile.put("schema", schema);
        
        List<Map<String, String>> rows = dataReader.readRows(dataset);
        List<Map<String, Object>> fieldStatsList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue().toString();
            
            Map<String, Object> fieldStat = new LinkedHashMap<>();
            fieldStat.put("fieldName", fieldName);
            fieldStat.put("fieldType", fieldType);
            
            if ("NUMERIC".equals(fieldType)) {
                if (!rows.isEmpty()) {
                    List<Double> values = new ArrayList<>();
                    for (Map<String, String> row : rows) {
                        Object val = row.get(fieldName);
                        if (val != null) {
                            try {
                                values.add(Double.parseDouble(val.toString()));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    if (!values.isEmpty()) {
                        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                        fieldStat.put("minValue", min);
                        fieldStat.put("maxValue", max);
                        fieldStat.put("meanValue", avg);
                        double variance = values.stream()
                                .mapToDouble(v -> Math.pow(v - avg, 2))
                                .average().orElse(0);
                        fieldStat.put("stdDev", Math.sqrt(variance));
                    }
                }
            }

            if (!rows.isEmpty()) {
                long distinctCount = rows.stream()
                        .map(r -> r.get(fieldName))
                        .filter(Objects::nonNull)
                        .filter(s -> !s.isBlank())
                        .distinct()
                        .count();
                fieldStat.put("distinctCount", distinctCount);
            }
            
            fieldStatsList.add(fieldStat);
        }
        
        profile.put("fieldStatistics", fieldStatsList);
        return profile;
    }
    
    private Map<String, Object> detectOutliers(Dataset dataset, Long taskId) throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> outliers = new ArrayList<>();
        
        Map<String, Object> schema = parseSchema(dataset.getSchemaJson());
        List<Map<String, String>> rows = dataReader.readRows(dataset);
        
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue().toString();
            
            if ("NUMERIC".equals(fieldType) && !rows.isEmpty()) {
                List<Integer> validIndices = new ArrayList<>();
                List<Double> values = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    Map<String, String> row = rows.get(i);
                    String val = row.get(fieldName);
                    if (val != null && !val.isBlank()) {
                        try {
                            values.add(Double.parseDouble(val));
                            validIndices.add(i);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                
                if (values.size() > 1) {
                    double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double stdDev = Math.sqrt(values.stream()
                            .mapToDouble(v -> Math.pow(v - mean, 2))
                            .average().orElse(0));
                    
                    // 计算Q1/Q3用于IQR
                    List<Double> sorted = new ArrayList<>(values);
                    Collections.sort(sorted);
                    double q1 = percentile(sorted, 25);
                    double q3 = percentile(sorted, 75);
                    double iqr = q3 - q1;
                    double lowerBound = q1 - 1.5 * iqr;
                    double upperBound = q3 + 1.5 * iqr;
                    
                    for (int i = 0; i < values.size(); i++) {
                        double v = values.get(i);
                        double zScore = stdDev > 0 ? Math.abs(v - mean) / stdDev : 0;
                        boolean isOutlier = zScore > 1.5 || v < lowerBound || v > upperBound;
                        if (isOutlier) {
                            Map<String, Object> outlier = new LinkedHashMap<>();
                            outlier.put("fieldName", fieldName);
                            outlier.put("rowIndex", validIndices.get(i));
                            outlier.put("value", v);
                            outlier.put("zScore", Math.round(zScore * 100.0) / 100.0);
                            outlier.put("outlierType", zScore > 3 ? "EXTREME" : "MODERATE");
                            outlier.put("detectionMethod", "Z_SCORE+IQR");
                            outliers.add(outlier);
                        }
                    }
                }
            }
        }
        
        results.put("outliers", outliers);
        results.put("totalOutliers", outliers.size());
        return results;
    }

    private double percentile(List<Double> sorted, int percentile) {
        if (sorted.isEmpty()) return 0;
        double index = (percentile / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        return sorted.get(lower) * (upper - index) + sorted.get(upper) * (index - lower);
    }
    
    private Map<String, Object> analyzeDataQuality(Dataset dataset) throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, String>> rows;
        try {
            rows = dataReader.readRows(dataset);
        } catch (Exception e) {
            log.error("Failed to read rows for quality analysis", e);
            results.put("error", "Failed to read dataset: " + e.getMessage());
            results.put("assessmentTime", LocalDateTime.now().toString());
            return results;
        }
        int totalRows = rows.size();
        
        Map<String, Object> schema = parseSchema(dataset.getSchemaJson());
        int totalColumns = schema.size();
        int totalCells = totalRows * Math.max(1, totalColumns);

        int nullCells = 0;
        int emptyCells = 0;
        Map<String, Integer> nullCountByColumn = new HashMap<>();
        Map<String, Integer> emptyCountByColumn = new HashMap<>();
        Map<String, Integer> invalidFormatByColumn = new HashMap<>();
        Map<String, Integer> outlierCountByColumn = new HashMap<>();
        Map<String, List<Integer>> invalidRowsByColumn = new HashMap<>();

        // 重复行检测：使用完整行哈希
        Map<String, List<Integer>> rowFingerprintMap = new LinkedHashMap<>();
        int duplicateRowGroups = 0;
        int duplicateRowCount = 0;
        List<Map<String, Object>> duplicateGroups = new ArrayList<>();

        int rowIndex = 0;
        for (Map<String, String> row : rows) {
            StringBuilder fpBuilder = new StringBuilder();
            for (String col : schema.keySet()) {
                String v = row.get(col);
                fpBuilder.append(v == null ? "\u0001" : v.trim()).append("|");
            }
            String fingerprint = sha256(fpBuilder.toString());

            List<Integer> group = rowFingerprintMap.computeIfAbsent(fingerprint, k -> new ArrayList<>());
            group.add(rowIndex);
            if (group.size() == 2) {
                duplicateRowGroups++;
            }
            if (group.size() > 1) {
                duplicateRowCount++;
            }
            rowIndex++;
        }

        for (List<Integer> group : rowFingerprintMap.values()) {
            if (group.size() > 1) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("count", group.size());
                g.put("rowIndices", group);
                duplicateGroups.add(g);
            }
        }

        // 计算各列的空值、空字符串、非法格式
        List<Map<String, Object>> columnIssues = new ArrayList<>();
        int criticalIssueCount = 0;
        int warningIssueCount = 0;
        int infoIssueCount = 0;

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String column = entry.getKey();
            String type = entry.getValue() != null ? entry.getValue().toString() : "STRING";

            int nullCount = 0;
            int emptyCount = 0;
            int invalidFormatCount = 0;
            List<Integer> invalidRows = new ArrayList<>();
            List<Integer> nullRows = new ArrayList<>();
            List<Integer> emptyRows = new ArrayList<>();

            for (int r = 0; r < rows.size(); r++) {
                Map<String, String> row = rows.get(r);
                String value = row.get(column);
                if (value == null) {
                    nullCount++;
                    nullRows.add(r);
                } else if (value.isBlank()) {
                    emptyCount++;
                    emptyRows.add(r);
                } else if (!isFormatValid(value, type)) {
                    invalidFormatCount++;
                    invalidRows.add(r);
                }
            }

            nullCells += nullCount;
            emptyCells += emptyCount;
            nullCountByColumn.put(column, nullCount);
            emptyCountByColumn.put(column, emptyCount);
            invalidFormatByColumn.put(column, invalidFormatCount);
            invalidRowsByColumn.put(column, invalidRows);

            int columnIssuesCount = nullCount + emptyCount + invalidFormatCount;
            if (columnIssuesCount > 0) {
                double nullRate = totalRows > 0 ? (double) (nullCount + emptyCount) / totalRows : 0;
                Map<String, Object> issue = new LinkedHashMap<>();
                issue.put("columnName", column);
                issue.put("columnType", type);
                issue.put("nullCount", nullCount);
                issue.put("emptyCount", emptyCount);
                issue.put("invalidFormatCount", invalidFormatCount);
                issue.put("nullRate", round(nullRate));
                issue.put("nullRows", nullRows);
                issue.put("emptyRows", emptyRows);
                issue.put("invalidRows", invalidRows);
                columnIssues.add(issue);

                if (nullRate > 0.2 || invalidFormatCount > 0) {
                    criticalIssueCount += invalidFormatCount;
                    warningIssueCount += nullCount + emptyCount;
                } else if (nullRate > 0.05) {
                    infoIssueCount += nullCount + emptyCount;
                }
            }
        }

        // 数值型列的离群值检测
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String column = entry.getKey();
            String type = entry.getValue() != null ? entry.getValue().toString() : "STRING";
            if (!"NUMERIC".equals(type)) continue;

            List<Double> validValues = new ArrayList<>();
            List<Integer> validIndices = new ArrayList<>();
            for (int r = 0; r < rows.size(); r++) {
                String v = rows.get(r).get(column);
                if (v != null && !v.isBlank()) {
                    try {
                        validValues.add(Double.parseDouble(v));
                        validIndices.add(r);
                    } catch (NumberFormatException ignored) {}
                }
            }

            int outlierCount = 0;
            if (validValues.size() > 1) {
                double mean = validValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double variance = validValues.stream()
                        .mapToDouble(v -> Math.pow(v - mean, 2))
                        .average().orElse(0);
                double stdDev = Math.sqrt(variance);

                List<Double> sorted = new ArrayList<>(validValues);
                Collections.sort(sorted);
                double q1 = percentile(sorted, 25);
                double q3 = percentile(sorted, 75);
                double iqr = q3 - q1;
                double lowerFence = q1 - 1.5 * iqr;
                double upperFence = q3 + 1.5 * iqr;

                for (int i = 0; i < validValues.size(); i++) {
                    double v = validValues.get(i);
                    double z = stdDev > 0 ? Math.abs(v - mean) / stdDev : 0;
                    if (z > 3 || v < lowerFence || v > upperFence || v < 0) {
                        outlierCount++;
                    }
                }
            }
            outlierCountByColumn.put(column, outlierCount);
        }

        // 计算质量指标
        int totalIssues = criticalIssueCount + warningIssueCount + infoIssueCount;
        double completeness = totalCells == 0 ? 1.0 : Math.max(0, (double) (totalCells - nullCells - emptyCells) / totalCells);
        double uniqueness = totalRows == 0 ? 1.0 : (double) (totalRows - duplicateRowCount) / totalRows;
        double accuracy = calculateAccuracy(rows, schema);
        double validity = calculateValidity(rows, schema, invalidFormatByColumn);
        double consistency = calculateConsistency(rows, schema);

        // 识别负价格等业务规则异常
        List<Map<String, Object>> ruleViolations = new ArrayList<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String column = entry.getKey();
            String type = entry.getValue() != null ? entry.getValue().toString() : "STRING";
            if (!"NUMERIC".equals(type)) continue;

            for (int r = 0; r < rows.size(); r++) {
                String v = rows.get(r).get(column);
                if (v == null || v.isBlank()) continue;
                try {
                    double num = Double.parseDouble(v);
                    if (num < 0 && (column.toLowerCase().contains("price") || column.toLowerCase().contains("sales")
                            || column.toLowerCase().contains("amount") || column.toLowerCase().contains("revenue"))) {
                        Map<String, Object> violation = new LinkedHashMap<>();
                        violation.put("columnName", column);
                        violation.put("rowIndex", r);
                        violation.put("value", num);
                        violation.put("rule", "NON_NEGATIVE");
                        violation.put("message", "数值字段 " + column + " 存在负值 " + num);
                        ruleViolations.add(violation);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 整体评分
        double overallScore = completeness * 0.30 + uniqueness * 0.20 + accuracy * 0.20
                + validity * 0.15 + consistency * 0.15;
        overallScore = Math.max(0, Math.min(1, overallScore));
        double rawScore = overallScore * 100;
        double weightedScore = Math.round(rawScore * 100.0) / 100.0;

        Map<String, Object> deductionDetails = new LinkedHashMap<>();
        List<Map<String, Object>> deductionItems = new ArrayList<>();
        addDeduction(deductionItems, "completeness", (1 - completeness) * 30,
                "空值/空字符串导致完整性扣分", nullCells + emptyCells);
        addDeduction(deductionItems, "uniqueness", (1 - uniqueness) * 20,
                "重复行导致唯一性扣分", duplicateRowCount);
        addDeduction(deductionItems, "accuracy", (1 - accuracy) * 20,
                "格式错误导致准确性扣分", criticalIssueCount);
        addDeduction(deductionItems, "validity", (1 - validity) * 15,
                "业务规则违反导致有效性扣分", ruleViolations.size());
        addDeduction(deductionItems, "consistency", (1 - consistency) * 15,
                "字段离散度导致一致性扣分", 0);
        deductionDetails.put("items", deductionItems);
        deductionDetails.put("totalDeduction", round(100 - weightedScore));

        Map<String, Object> qualityMetrics = new LinkedHashMap<>();
        qualityMetrics.put("completeness", round(completeness));
        qualityMetrics.put("uniqueness", round(uniqueness));
        qualityMetrics.put("accuracy", round(accuracy));
        qualityMetrics.put("consistency", round(consistency));
        qualityMetrics.put("validity", round(validity));
        qualityMetrics.put("timeliness", 1.0);

        Map<String, Object> issueSummary = new LinkedHashMap<>();
        issueSummary.put("totalIssues", totalIssues + ruleViolations.size());
        issueSummary.put("criticalIssues", criticalIssueCount + ruleViolations.size());
        issueSummary.put("warningIssues", warningIssueCount);
        issueSummary.put("infoIssues", infoIssueCount);

        Map<String, Object> scoringRules = new LinkedHashMap<>();
        scoringRules.put("completenessWeight", 0.3);
        scoringRules.put("uniquenessWeight", 0.2);
        scoringRules.put("accuracyWeight", 0.2);
        scoringRules.put("consistencyWeight", 0.15);
        scoringRules.put("validityWeight", 0.15);
        scoringRules.put("scoreVersion", QUALITY_RULE_VERSION);

        results.put("qualityMetrics", qualityMetrics);
        results.put("issueSummary", issueSummary);
        results.put("columnIssues", columnIssues);
        results.put("duplicateRows", duplicateRowCount);
        results.put("duplicateRowGroups", duplicateGroups);
        results.put("duplicateRowGroupsCount", duplicateRowGroups);
        results.put("outlierCountByColumn", outlierCountByColumn);
        results.put("ruleViolations", ruleViolations);
        results.put("deductionDetails", deductionDetails);
        results.put("overallScore", weightedScore);
        results.put("scoringRules", scoringRules);
        results.put("totalRows", totalRows);
        results.put("totalColumns", totalColumns);
        results.put("totalCells", totalCells);
        results.put("nullCells", nullCells);
        results.put("emptyCells", emptyCells);
        results.put("duplicateRowsCount", duplicateRowCount);
        results.put("invalidFormatCount", invalidFormatByColumn.values().stream().mapToInt(Integer::intValue).sum());
        results.put("assessmentTime", LocalDateTime.now().toString());
        results.put("ruleVersion", QUALITY_RULE_VERSION);

        return results;
    }

    private void addDeduction(List<Map<String, Object>> items, String dimension, double deduction, String reason, long count) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dimension", dimension);
        item.put("deduction", round(deduction));
        item.put("reason", reason);
        item.put("affectedCount", count);
        items.add(item);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private boolean isFormatValid(String value, String type) {
        if (value == null || value.isBlank()) return true;
        if ("NUMERIC".equals(type)) {
            try {
                Double.parseDouble(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if ("DATE".equals(type)) {
            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    formatter.parse(value);
                    return true;
                } catch (DateTimeParseException ignored) {}
            }
            return false;
        }
        return true;
    }

    private double calculateAccuracy(List<Map<String, String>> rows, Map<String, Object> schema) {
        int validValues = 0;
        int totalChecked = 0;

        for (Map<String, String> row : rows) {
            for (Map.Entry<String, Object> entry : schema.entrySet()) {
                String column = entry.getKey();
                String type = entry.getValue() != null ? entry.getValue().toString() : "STRING";
                String value = row.get(column);

                if (value == null || value.isBlank()) continue;

                totalChecked++;
                if ("NUMERIC".equals(type)) {
                    try {
                        Double.parseDouble(value);
                        validValues++;
                    } catch (NumberFormatException ignored) {}
                } else if ("DATE".equals(type)) {
                    for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                        try {
                            formatter.parse(value);
                            validValues++;
                            break;
                        } catch (DateTimeParseException ignored) {}
                    }
                } else {
                    validValues++;
                }
            }
        }

        return totalChecked == 0 ? 1.0 : (double) validValues / totalChecked;
    }

    private double calculateConsistency(List<Map<String, String>> rows, Map<String, Object> schema) {
        int consistentValues = 0;
        int totalChecked = 0;

        for (Map<String, String> row : rows) {
            for (String column : schema.keySet()) {
                String value = row.get(column);
                if (value == null || value.isBlank()) continue;
                totalChecked++;
                if (value.length() <= 500) {
                    consistentValues++;
                }
            }
        }

        return totalChecked == 0 ? 1.0 : (double) consistentValues / totalChecked;
    }

    private double calculateValidity(List<Map<String, String>> rows, Map<String, Object> schema,
                                      Map<String, Integer> invalidFormatByColumn) {
        int validValues = 0;
        int totalChecked = 0;

        for (Map<String, String> row : rows) {
            for (Map.Entry<String, Object> entry : schema.entrySet()) {
                String column = entry.getKey();
                String value = row.get(column);

                if (value == null || value.isBlank()) continue;
                totalChecked++;
                if (value.length() <= 1000) {
                    validValues++;
                }
            }
        }

        return totalChecked == 0 ? 1.0 : (double) validValues / totalChecked;
    }

    private Map<String, Object> parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(schemaJson, new TypeReference<Object>() {});
            if (parsed instanceof Map) {
                Map<String, Object> original = (Map<String, Object>) parsed;
                Map<String, Object> ordered = new LinkedHashMap<>();
                original.keySet().stream().sorted().forEach(k -> ordered.put(k, original.get(k)));
                return ordered;
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("Failed to parse dataset schema: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
    
    public List<FieldStatistics> getFieldStatistics(Long datasetId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
        }
        return fieldStatisticsMapper.selectByDatasetId(datasetId);
    }
    
    public List<OutlierDetection> getOutliers(Long datasetId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            accessValidator.validateDatasetAccess(datasetId, securityUtils.getCurrentUserId(auth));
        }
        return outlierDetectionMapper.selectByDatasetId(datasetId);
    }
    
    public long getAnalysisTaskCount() {
        return analysisTaskMapper.selectCount(new LambdaQueryWrapper<>());
    }

    private void validateDatasetParsed(Dataset dataset) {
        String status = dataset.getStatus();
        if (!"PARSED".equals(status)) {
            throw new BusinessException("数据集尚未解析完成 (当前状态: " + status + ")，请先执行解析后再进行分析");
        }
        if (dataset.getSchemaJson() == null || dataset.getSchemaJson().isBlank()) {
            throw new BusinessException("数据集 Schema 为空，无法执行分析");
        }
        if (dataset.getRowCount() == null || dataset.getRowCount() <= 0) {
            throw new BusinessException("数据集行数为 0，无法执行分析");
        }
        if (dataset.getColumnCount() == null || dataset.getColumnCount() <= 0) {
            throw new BusinessException("数据集列数为 0，无法执行分析");
        }
    }
}
