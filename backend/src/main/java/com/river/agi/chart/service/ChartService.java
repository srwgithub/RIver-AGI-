package com.river.agi.chart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.analysis.entity.AnalysisTask;
import com.river.agi.analysis.mapper.AnalysisTaskMapper;
import com.river.agi.chart.entity.ChartConfig;
import com.river.agi.chart.entity.Report;
import com.river.agi.chart.mapper.ChartConfigMapper;
import com.river.agi.chart.mapper.ReportMapper;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.PageResult;
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

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {
    
    private final ChartConfigMapper chartConfigMapper;
    private final ReportMapper reportMapper;
    private final DatasetMapper datasetMapper;
    private final ObjectMapper objectMapper;
    private final DatasetDataReaderService dataReader;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final SecurityScanTaskMapper securityScanTaskMapper;
    private final PredictionTaskMapper predictionTaskMapper;
    private final PredictionResultMapper predictionResultMapper;
    private final SecurityUtils securityUtils;
    private final ResourceAccessValidator accessValidator;
    
    @AuditOperation(action = "RECOMMEND_CHARTS", resourceType = "CHART", description = "Recommend chart types for dataset")
    public List<Map<String, Object>> recommendCharts(Long datasetId) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        validateDatasetParsed(dataset);
        
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        try {
            Map<String, Object> schema = objectMapper.readValue(
                    dataset.getSchemaJson(), 
                    new TypeReference<Map<String, Object>>() {}
            );
            
            List<String> numericFields = new ArrayList<>();
            List<String> stringFields = new ArrayList<>();
            
            for (Map.Entry<String, Object> entry : schema.entrySet()) {
                String fieldType = entry.getValue().toString();
                if ("NUMERIC".equals(fieldType)) {
                    numericFields.add(entry.getKey());
                } else {
                    stringFields.add(entry.getKey());
                }
            }
            
            // Recommend line chart for time series
            if (stringFields.stream().anyMatch(f -> f.toLowerCase().contains("date") || f.toLowerCase().contains("time"))) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("chartType", "LINE");
                rec.put("description", "趋势分析图");
                rec.put("recommendedXField", stringFields.stream().filter(f -> f.toLowerCase().contains("date")).findFirst().orElse(stringFields.get(0)));
                rec.put("recommendedYField", numericFields.isEmpty() ? "value" : numericFields.get(0));
                recommendations.add(rec);
            }
            
            // Recommend bar chart for comparisons
            if (!stringFields.isEmpty() && !numericFields.isEmpty()) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("chartType", "BAR");
                rec.put("description", "对比柱状图");
                rec.put("recommendedXField", stringFields.get(0));
                rec.put("recommendedYField", numericFields.get(0));
                recommendations.add(rec);
            }
            
            // Recommend pie chart for proportions
            if (numericFields.size() >= 1) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("chartType", "PIE");
                rec.put("description", "占比饼图");
                rec.put("recommendedXField", stringFields.isEmpty() ? "category" : stringFields.get(0));
                rec.put("recommendedYField", numericFields.get(0));
                recommendations.add(rec);
            }
            
            // Recommend scatter chart for correlations
            if (numericFields.size() >= 2) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("chartType", "SCATTER");
                rec.put("description", "相关性散点图");
                rec.put("recommendedXField", numericFields.get(0));
                rec.put("recommendedYField", numericFields.get(1));
                recommendations.add(rec);
            }
            
            // Recommend histogram for distributions
            if (!numericFields.isEmpty()) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("chartType", "HISTOGRAM");
                rec.put("description", "数值分布图");
                rec.put("recommendedYField", numericFields.get(0));
                recommendations.add(rec);
            }
            
        } catch (Exception e) {
            log.error("Failed to recommend charts", e);
        }
        
        return recommendations;
    }
    
    @AuditOperation(action = "GENERATE_CHART", resourceType = "CHART", description = "Generate chart visualization")
    public Map<String, Object> generateChart(Long datasetId, String chartType, 
                                             String xAxisField, String yAxisField) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        validateDatasetParsed(dataset);
        
        Map<String, Object> chartData = new LinkedHashMap<>();
        chartData.put("chartType", chartType);
        chartData.put("title", generateTitle(chartType, xAxisField, yAxisField));
        chartData.put("xAxisField", xAxisField);
        chartData.put("yAxisField", yAxisField);
        
        List<Map<String, String>> rows = dataReader.readRows(dataset);
        List<String> xData = new ArrayList<>();
        List<Double> yData = new ArrayList<>();
        Map<String, Double> aggregates = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String x = xAxisField == null ? "全部" : row.getOrDefault(xAxisField, "");
            if (x.isBlank()) continue;
            double value = 1.0;
            if (yAxisField != null && !yAxisField.isBlank()) {
                try { value = Double.parseDouble(row.getOrDefault(yAxisField, "")); }
                catch (NumberFormatException e) { continue; }
            }
            aggregates.merge(x, value, Double::sum);
        }
        xData.addAll(aggregates.keySet());
        yData.addAll(aggregates.values().stream().map(v -> Math.round(v * 100.0) / 100.0).toList());
        
        chartData.put("xData", xData);
        chartData.put("yData", yData);
        
        return chartData;
    }
    
    private String generateTitle(String chartType, String xAxisField, String yAxisField) {
        String chartTypeName = switch (chartType.toUpperCase()) {
            case "LINE" -> "趋势图";
            case "BAR" -> "柱状图";
            case "PIE" -> "饼图";
            case "SCATTER" -> "散点图";
            case "HISTOGRAM" -> "直方图";
            case "AREA" -> "面积图";
            default -> "图表";
        };
        return String.format("%s - %s vs %s", chartTypeName, xAxisField, yAxisField);
    }
    
    public ChartConfig saveChartConfig(Long datasetId, String chartType, String title,
                                       String xAxisField, String yAxisField, 
                                       Map<String, Object> data, Authentication authentication) {
        ChartConfig config = new ChartConfig();
        config.setDatasetId(datasetId);
        config.setChartType(chartType);
        config.setTitle(title);
        
        try {
            Map<String, Object> configData = new LinkedHashMap<>();
            configData.put("xAxisField", xAxisField);
            configData.put("yAxisField", yAxisField);
            configData.put("chartType", chartType);
            configData.put("data", data);
            config.setConfigJson(objectMapper.writeValueAsString(configData));
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize chart data");
        }
        
        config.setCreatedAt(LocalDateTime.now());
        
        chartConfigMapper.insert(config);
        return config;
    }
    
    public List<ChartConfig> getChartConfigs(Long datasetId) {
        return chartConfigMapper.selectByDatasetId(datasetId);
    }
    
    @AuditOperation(action = "GENERATE_REPORT", resourceType = "REPORT", description = "Generate data analysis report")
    public Report generateReport(Long datasetId, String reportType, Authentication authentication) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("Dataset not found");
        }

        validateDatasetParsed(dataset);

        Long userId = authentication == null ? null : securityUtils.getCurrentUserId(authentication);
        if (userId != null) {
            accessValidator.validateDatasetOwnership(datasetId, userId);
        }
        
        Report report = new Report();
        report.setTitle("数据报告 - " + dataset.getName());
        report.setDatasetId(datasetId);
        report.setReportType(reportType);
        report.setCreatedAt(LocalDateTime.now());
        
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", report.getTitle());
        content.put("generatedAt", LocalDateTime.now().toString());
        content.put("datasetName", dataset.getName());
        content.put("rowCount", dataset.getRowCount());
        content.put("columnCount", dataset.getColumnCount());
        
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("dataOverview", Map.of(
                "description", "数据概览",
                "rows", dataset.getRowCount(),
                "columns", dataset.getColumnCount()
        ));
        
        Map<String, Object> qualitySection = buildQualitySection(datasetId);
        sections.put("dataQuality", qualitySection);
        
        Map<String, Object> securitySection = buildSecuritySection(datasetId);
        sections.put("securityOverview", securitySection);
        
        Map<String, Object> predictionSection = buildPredictionSection(datasetId);
        sections.put("predictionOverview", predictionSection);
        
        List<String> keyInsights = generateKeyInsights(qualitySection, securitySection, predictionSection, dataset);
        sections.put("keyInsights", keyInsights);
        
        List<String> recommendations = generateRecommendations(qualitySection, securitySection, predictionSection);
        sections.put("recommendations", recommendations);
        
        content.put("sections", sections);
        
        try {
            report.setContent(objectMapper.writeValueAsString(content));
        } catch (Exception e) {
            throw new BusinessException("Failed to generate report content");
        }
        
        reportMapper.insert(report);
        return report;
    }
    
    private Map<String, Object> buildQualitySection(Long datasetId) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("description", "数据质量评估");
        
        List<AnalysisTask> qualityTasks = analysisTaskMapper.selectList(
                new LambdaQueryWrapper<AnalysisTask>()
                        .eq(AnalysisTask::getDatasetId, datasetId)
                        .eq(AnalysisTask::getTaskType, "QUALITY")
                        .orderByDesc(AnalysisTask::getCreatedAt)
                        .last("LIMIT 1")
        );
        
        if (qualityTasks != null && !qualityTasks.isEmpty()) {
            AnalysisTask latestQuality = qualityTasks.get(0);
            if (latestQuality.getResultJson() != null) {
                try {
                    Map<String, Object> qualityResult = objectMapper.readValue(
                            latestQuality.getResultJson(),
                            new TypeReference<Map<String, Object>>() {}
                    );
                    section.put("status", latestQuality.getStatus());
                    section.put("overallScore", qualityResult.get("overallScore"));
                    section.put("completeness", qualityResult.get("completeness"));
                    section.put("uniqueness", qualityResult.get("uniqueness"));
                    section.put("accuracy", qualityResult.get("accuracy"));
                    section.put("consistency", qualityResult.get("consistency"));
                    section.put("validity", qualityResult.get("validity"));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> issues = (List<Map<String, Object>>) qualityResult.get("columnIssues");
                    if (issues != null && !issues.isEmpty()) {
                        long issueCount = issues.stream()
                                .filter(c -> ((Number) c.getOrDefault("nullRate", 0)).doubleValue() > 0.1)
                                .count();
                        section.put("issueColumns", issueCount);
                    }
                } catch (Exception e) {
                    section.put("note", "质量分析结果解析失败");
                }
            } else {
                section.put("note", "质量分析任务已创建但尚未完成");
            }
        } else {
            section.put("note", "尚未执行数据质量分析，建议先运行质量评估");
        }
        
        return section;
    }
    
    private Map<String, Object> buildSecuritySection(Long datasetId) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("description", "安全扫描概览");
        
        List<SecurityScanTask> scanTasks = securityScanTaskMapper.selectList(
                new LambdaQueryWrapper<SecurityScanTask>()
                        .eq(SecurityScanTask::getDatasetId, datasetId)
                        .orderByDesc(SecurityScanTask::getScanTime)
                        .last("LIMIT 1")
        );
        
        if (scanTasks != null && !scanTasks.isEmpty()) {
            SecurityScanTask latestScan = scanTasks.get(0);
            section.put("scanTime", latestScan.getScanTime());
            section.put("status", latestScan.getStatus());
            section.put("totalFields", latestScan.getTotalFields());
            section.put("sensitiveFieldsFound", latestScan.getSensitiveFieldsFound());
            section.put("highRiskCount", latestScan.getHighRiskCount());
            section.put("mediumRiskCount", latestScan.getMediumRiskCount());
            section.put("lowRiskCount", latestScan.getLowRiskCount());
        } else {
            section.put("status", "NOT_STARTED");
            section.put("note", "尚未执行安全扫描，建议检查敏感数据风险");
        }
        
        return section;
    }
    
    private Map<String, Object> buildPredictionSection(Long datasetId) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("description", "预测分析概览");
        
        List<PredictionTask> tasks = predictionTaskMapper.selectList(
                new LambdaQueryWrapper<PredictionTask>()
                        .eq(PredictionTask::getDatasetId, datasetId)
                        .orderByDesc(PredictionTask::getCreatedAt)
                        .last("LIMIT 5")
        );
        
        if (tasks == null || tasks.isEmpty()) {
            section.put("status", "NOT_STARTED");
            section.put("note", "尚未执行预测分析");
            return section;
        }
        
        long completedCount = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long failedCount = tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();
        section.put("totalTasks", tasks.size());
        section.put("completedTasks", completedCount);
        section.put("failedTasks", failedCount);
        
        List<Map<String, Object>> taskSummaries = new ArrayList<>();
        double totalProjectedGrowth = 0.0;
        int trendUpCount = 0;
        int trendDownCount = 0;
        
        for (PredictionTask task : tasks) {
            if (!"COMPLETED".equals(task.getStatus())) continue;
            List<PredictionResult> results = predictionResultMapper.selectByTaskId(task.getId());
            if (results == null || results.isEmpty()) continue;
            
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("taskId", task.getId());
            summary.put("targetField", task.getTargetField());
            summary.put("modelType", task.getModelType());
            summary.put("resultCount", results.size());
            
            double firstPred = results.get(0).getPredictedValue() == null ? 0 : results.get(0).getPredictedValue();
            double lastPred = results.get(results.size() - 1).getPredictedValue() == null ? 0 : results.get(results.size() - 1).getPredictedValue();
            double growth = firstPred == 0 ? 0 : (lastPred - firstPred) / Math.abs(firstPred) * 100;
            summary.put("projectedGrowthPercent", Math.round(growth * 100.0) / 100.0);
            totalProjectedGrowth += growth;
            if (growth > 1) trendUpCount++;
            else if (growth < -1) trendDownCount++;
            
            double avgConfidence = results.stream()
                    .mapToDouble(r -> r.getConfidence() == null ? 0 : r.getConfidence())
                    .average().orElse(0.0);
            summary.put("averageConfidence", Math.round(avgConfidence * 1000.0) / 1000.0);
            
            taskSummaries.add(summary);
        }
        
        section.put("taskSummaries", taskSummaries);
        section.put("averageProjectedGrowthPercent", Math.round(totalProjectedGrowth * 100.0) / 100.0);
        section.put("trendUpCount", trendUpCount);
        section.put("trendDownCount", trendDownCount);
        section.put("trendFlatCount", completedCount - trendUpCount - trendDownCount);
        
        return section;
    }
    
    private List<String> generateKeyInsights(Map<String, Object> qualitySection,
                                              Map<String, Object> securitySection,
                                              Map<String, Object> predictionSection,
                                              Dataset dataset) {
        List<String> insights = new ArrayList<>();
        
        Object qualityScore = qualitySection.get("overallScore");
        if (qualityScore instanceof Number) {
            double score = ((Number) qualityScore).doubleValue();
            Object completeness = qualitySection.get("completeness");
            Object accuracy = qualitySection.get("accuracy");
            StringBuilder qNote = new StringBuilder();
            qNote.append(String.format("数据质量分数为 %.2f", score));
            if (completeness instanceof Number) {
                qNote.append(String.format("，完整性 %.1f%%", ((Number) completeness).doubleValue() * 100));
            }
            if (accuracy instanceof Number) {
                qNote.append(String.format("，准确性 %.1f%%", ((Number) accuracy).doubleValue() * 100));
            }
            if (score >= 0.9) {
                qNote.append("，处于优秀水平，可直接用于分析与建模");
            } else if (score >= 0.7) {
                qNote.append("，处于良好水平，建议关注少量异常字段");
            } else {
                qNote.append("，低于 0.7 阈值，建议先修复质量问题再投入使用");
            }
            insights.add(qNote.toString());
        } else {
            insights.add(String.format("数据集 \"%s\"(%d 行/%d 列) 尚未完成质量分析，建议尽快执行质量评估以建立基线",
                    dataset.getName(), dataset.getRowCount(), dataset.getColumnCount()));
        }
        
        Object highRisk = securitySection.get("highRiskCount");
        Object mediumRisk = securitySection.get("mediumRiskCount");
        Object lowRisk = securitySection.get("lowRiskCount");
        Object totalFields = securitySection.get("totalFields");
        if (securitySection.get("status") != null && !"NOT_STARTED".equals(securitySection.get("status"))) {
            int high = highRisk instanceof Number ? ((Number) highRisk).intValue() : 0;
            int medium = mediumRisk instanceof Number ? ((Number) mediumRisk).intValue() : 0;
            int low = lowRisk instanceof Number ? ((Number) lowRisk).intValue() : 0;
            int totalF = totalFields instanceof Number ? ((Number) totalFields).intValue() : 0;
            int sensitive = high + medium + low;
            double pct = totalF == 0 ? 0 : (sensitive * 100.0) / totalF;
            
            if (high > 0) {
                insights.add(String.format("安全扫描共检测到 %d 个敏感字段(占 %.1f%%)，其中 %d 个为高风险，必须立即采取脱敏或访问控制措施",
                        sensitive, pct, high));
            } else if (medium > 0) {
                insights.add(String.format("安全扫描检测到 %d 个敏感字段(占 %.1f%%)，其中 %d 个为中风险，建议进行脱敏处理",
                        sensitive, pct, medium));
            } else if (sensitive > 0) {
                insights.add(String.format("安全扫描检测到 %d 个低风险敏感字段(占 %.1f%%)，建议记录并定期审计",
                        sensitive, pct));
            } else {
                insights.add(String.format("安全扫描覆盖 %d 个字段，未发现敏感数据风险，状态良好", totalF));
            }
        } else {
            insights.add(String.format("数据集 \"%s\" 尚未执行安全扫描，建议在投入生产前完成扫描以识别敏感数据风险", dataset.getName()));
        }
        
        Object predStatus = predictionSection.get("status");
        if (!"NOT_STARTED".equals(predStatus)) {
            Object avgGrowth = predictionSection.get("averageProjectedGrowthPercent");
            Object trendUp = predictionSection.get("trendUpCount");
            Object trendDown = predictionSection.get("trendDownCount");
            Object completed = predictionSection.get("completedTasks");
            int comp = completed instanceof Number ? ((Number) completed).intValue() : 0;
            int up = trendUp instanceof Number ? ((Number) trendUp).intValue() : 0;
            int down = trendDown instanceof Number ? ((Number) trendDown).intValue() : 0;
            
            if (comp > 0) {
                StringBuilder pNote = new StringBuilder();
                pNote.append(String.format("已完成 %d 个预测任务", comp));
                if (avgGrowth instanceof Number) {
                    double g = ((Number) avgGrowth).doubleValue();
                    pNote.append(String.format("，整体预期增长 %.2f%%", g));
                }
                pNote.append(String.format("，其中 %d 个指标呈上升趋势、%d 个呈下降趋势", up, down));
                insights.add(pNote.toString());
            }
        } else {
            insights.add(String.format("数据集 \"%s\" 尚未建立预测模型，建议基于历史数据构建趋势预测以支持决策", dataset.getName()));
        }
        
        if (insights.isEmpty()) {
            insights.add("当前数据集尚未完成质量与安全分析，建议尽快执行全量评估");
        }
        
        return insights;
    }
    
    private List<String> generateRecommendations(Map<String, Object> qualitySection,
                                                  Map<String, Object> securitySection,
                                                  Map<String, Object> predictionSection) {
        List<String> recommendations = new ArrayList<>();
        
        Object qualityScore = qualitySection.get("overallScore");
        if (qualityScore instanceof Number) {
            double score = ((Number) qualityScore).doubleValue();
            if (score < 0.7) {
                recommendations.add("质量分数低于 0.7，建议优先处理缺失值、异常值与重复值后再进行建模");
            }
            Object issueColumns = qualitySection.get("issueColumns");
            if (issueColumns instanceof Number && ((Number) issueColumns).intValue() > 0) {
                recommendations.add(String.format("有 %d 个字段存在质量问题，建议重点关注字段级的数据修复策略",
                        ((Number) issueColumns).intValue()));
            }
        }
        
        Object highRisk = securitySection.get("highRiskCount");
        Object mediumRisk = securitySection.get("mediumRiskCount");
        if (highRisk instanceof Number && ((Number) highRisk).intValue() > 0) {
            recommendations.add("存在高风险敏感字段，建议启用字段级加密、脱敏导出与细粒度权限控制");
        } else if (mediumRisk instanceof Number && ((Number) mediumRisk).intValue() > 0) {
            recommendations.add("存在中风险敏感字段，建议对相关字段启用动态脱敏与访问审计");
        }
        if ("NOT_STARTED".equals(securitySection.get("status"))) {
            recommendations.add("建议尽快完成安全扫描，建立敏感字段清单与风险基线");
        }
        
        if ("NOT_STARTED".equals(predictionSection.get("status"))) {
            recommendations.add("建议基于历史数据建立至少一个时间序列预测模型(Holt-Winters/指数平滑)并定期重训");
        } else {
            Object avgGrowth = predictionSection.get("averageProjectedGrowthPercent");
            if (avgGrowth instanceof Number && ((Number) avgGrowth).doubleValue() < -10) {
                recommendations.add("预测显示整体趋势下降超过 10%，建议结合业务场景进行根因分析并制定应对预案");
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("当前数据集质量、安全与预测状态均良好，建议建立定期巡检与重训机制以保持数据活性");
        }
        
        return recommendations;
    }
    
    public PageResult<Report> getReports(int page, int size, Long datasetId) {
        Page<Report> pageRequest = new Page<>(page, size);
        Page<Report> pageResult;
        
        if (datasetId != null) {
            pageResult = reportMapper.selectByDatasetId(pageRequest, datasetId);
        } else {
            pageResult = reportMapper.selectPage(pageRequest, 
                    new LambdaQueryWrapper<Report>().orderByDesc(Report::getCreatedAt));
        }
        
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
    
    public Report getReport(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("Report not found");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Long userId = securityUtils.getCurrentUserId(auth);
            accessValidator.validateReportAccess(reportId, userId);
        }
        return report;
    }
    
    public void deleteReport(Long reportId) {
        reportMapper.deleteById(reportId);
    }

    private void validateDatasetParsed(Dataset dataset) {
        String status = dataset.getStatus();
        if (!"PARSED".equals(status)) {
            throw new BusinessException("数据集尚未解析完成 (当前状态: " + status + ")，请先执行解析后再进行图表分析");
        }
        if (dataset.getSchemaJson() == null || dataset.getSchemaJson().isBlank()) {
            throw new BusinessException("数据集 Schema 为空，无法生成图表");
        }
        if (dataset.getRowCount() == null || dataset.getRowCount() <= 0) {
            throw new BusinessException("数据集行数为 0，无法生成图表");
        }
    }
}
