package com.river.agi.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dashboard.entity.ReportInstance;
import com.river.agi.dashboard.entity.ReportTemplate;
import com.river.agi.dashboard.mapper.ReportInstanceMapper;
import com.river.agi.dashboard.mapper.ReportTemplateMapper;
import com.river.agi.trend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportTemplateMapper templateMapper;
    private final ReportInstanceMapper instanceMapper;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private final TrendDiagnosisService trendDiagnosisService;
    private final ComparisonAnalysisService comparisonService;
    private final AnomalyDetectionService anomalyService;
    private final RootCauseAnalysisService rcaService;
    private final OlapAnalysisService olapService;
    private final DecisionSupportService decisionService;

    public List<ReportTemplate> listTemplates(Long datasetId, String reportType) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<ReportTemplate>()
                .eq(ReportTemplate::getDeleted, 0)
                .orderByDesc(ReportTemplate::getUpdatedAt);
        if (datasetId != null) wrapper.eq(ReportTemplate::getDatasetId, datasetId);
        if (reportType != null) wrapper.eq(ReportTemplate::getReportType, reportType);
        return templateMapper.selectList(wrapper);
    }

    public ReportTemplate createTemplate(ReportTemplate template, Authentication auth) {
        template.setTenantId(1L);
        template.setCreatedBy(securityUtils.getCurrentUserId(auth));
        template.setDeleted(0);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        if (template.getSectionsJson() == null) template.setSectionsJson(getDefaultSections(template.getReportType()));
        if (template.getParametersJson() == null) template.setParametersJson("{}");
        templateMapper.insert(template);
        return template;
    }

    public ReportTemplate updateTemplate(Long id, ReportTemplate updates, Authentication auth) {
        ReportTemplate t = templateMapper.selectById(id);
        if (t == null || t.getDeleted() == 1) throw new BusinessException("模板不存在");
        if (updates.getName() != null) t.setName(updates.getName());
        if (updates.getDescription() != null) t.setDescription(updates.getDescription());
        if (updates.getSectionsJson() != null) t.setSectionsJson(updates.getSectionsJson());
        if (updates.getParametersJson() != null) t.setParametersJson(updates.getParametersJson());
        t.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(t);
        return t;
    }

    public void deleteTemplate(Long id) {
        ReportTemplate t = templateMapper.selectById(id);
        if (t == null) return;
        t.setDeleted(1);
        t.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(t);
    }

    public ReportInstance generateReport(Long templateId, Long datasetId, Long predictionTaskId,
                                          String exportFormat, Authentication auth) {
        ReportTemplate template = templateId != null ? templateMapper.selectById(templateId) : null;
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("generatedAt", LocalDateTime.now().toString());
        content.put("generatedBy", securityUtils.getCurrentUserId(auth));
        content.put("datasetId", datasetId);

        Map<String, Object> sections = new LinkedHashMap<>();

        try {
            Map<String, Object> kpiData = olapService.getKpiDashboard(datasetId, getDefaultMeasure(datasetId), getDefaultTimeField(datasetId));
            sections.put("overview", Map.of(
                    "title", "数据概览",
                    "type", "KPI_SUMMARY",
                    "data", kpiData
            ));
        } catch (Exception e) {
            log.warn("KPI section generation failed", e);
        }

        if (predictionTaskId != null) {
            try {
                var diagnosis = trendDiagnosisService.diagnose(predictionTaskId);
                sections.put("trendDiagnosis", Map.of(
                        "title", "趋势诊断",
                        "type", "TREND_DIAGNOSIS",
                        "data", diagnosis
                ));
            } catch (Exception e) {
                log.warn("Trend diagnosis section failed", e);
            }

            try {
                var comparison = comparisonService.getActualVsPredicted(predictionTaskId);
                sections.put("comparison", Map.of(
                        "title", "预测对比分析",
                        "type", "COMPARISON",
                        "data", comparison
                ));
            } catch (Exception e) {
                log.warn("Comparison section failed", e);
            }

            try {
                var alerts = anomalyService.getAlerts(datasetId, predictionTaskId, null, "OPEN");
                var alertSummary = anomalyService.getAlertSummary(datasetId);
                sections.put("anomalies", Map.of(
                        "title", "异常检测",
                        "type", "ANOMALY_ALERTS",
                        "data", Map.of("summary", alertSummary, "alerts", alerts)
                ));
            } catch (Exception e) {
                log.warn("Anomaly section failed", e);
            }
        }

        try {
            String measure = getDefaultMeasure(datasetId);
            var decisions = decisionService.getDecisionRecommendations(datasetId, measure);
            sections.put("decisions", Map.of(
                    "title", "决策建议",
                    "type", "DECISION_SUPPORT",
                    "data", decisions
            ));
        } catch (Exception e) {
            log.warn("Decision section failed", e);
        }

        content.put("sections", sections);
        content.put("summary", generateReportSummary(sections));

        ReportInstance instance = new ReportInstance();
        instance.setTemplateId(templateId);
        instance.setDatasetId(datasetId);
        instance.setTitle((template != null ? template.getName() : "趋势分析报告") + " - " + LocalDateTime.now().toLocalDate());
        instance.setContentJson(safeJson(content));
        instance.setExportFormat(exportFormat != null ? exportFormat : "JSON");
        instance.setStatus("GENERATED");
        instance.setGeneratedBy(securityUtils.getCurrentUserId(auth));
        instance.setGeneratedAt(LocalDateTime.now());
        instance.setTenantId(1L);
        instance.setCreatedAt(LocalDateTime.now());
        instanceMapper.insert(instance);
        return instance;
    }

    public ReportInstance getReportInstance(Long id) {
        ReportInstance ri = instanceMapper.selectById(id);
        if (ri == null || ri.getDeleted() == 1) throw new BusinessException("报告不存在");
        return ri;
    }

    public List<ReportInstance> listReportInstances(Long datasetId) {
        return instanceMapper.selectList(
                new LambdaQueryWrapper<ReportInstance>()
                        .eq(ReportInstance::getDeleted, 0)
                        .eq(datasetId != null, ReportInstance::getDatasetId, datasetId)
                        .orderByDesc(ReportInstance::getGeneratedAt)
        );
    }

    public Map<String, Object> getDefaultTrendDashboardData(Long datasetId, Long predictionTaskId) {
        return getDefaultTrendDashboardData(datasetId, predictionTaskId, null, null);
    }

    public Map<String, Object> getDefaultTrendDashboardData(Long datasetId, Long predictionTaskId, String requestedMeasure, String requestedTimeField) {
        Map<String, Object> data = new LinkedHashMap<>();
        String measure = requestedMeasure != null && !requestedMeasure.isBlank() ? requestedMeasure : getDefaultMeasure(datasetId);
        String timeField = requestedTimeField != null && !requestedTimeField.isBlank() ? requestedTimeField : getDefaultTimeField(datasetId);

        try { data.put("kpi", olapService.getKpiDashboard(datasetId, measure, timeField)); }
        catch (Exception e) { data.put("kpi", Map.of("error", e.getMessage())); }

        if (predictionTaskId != null) {
            try { data.put("trendDiagnosis", trendDiagnosisService.getLatestDiagnosis(predictionTaskId)); }
            catch (Exception e) { log.warn("diagnosis load failed", e); }
            try { data.put("actualVsPredicted", comparisonService.getActualVsPredicted(predictionTaskId)); }
            catch (Exception e) { log.warn("comparison load failed", e); }
            try { data.put("alertSummary", anomalyService.getAlertSummary(datasetId)); }
            catch (Exception e) { log.warn("alert summary failed", e); }
            try { data.put("scenarios", decisionService.generateThreeScenarios(predictionTaskId)); }
            catch (Exception e) { log.warn("scenarios failed", e); }
        }

        try { data.put("recommendations", decisionService.getDecisionRecommendations(datasetId, measure)); }
        catch (Exception e) { log.warn("recommendations failed", e); }

        try {
            data.put("contribution", rcaService.getContributionBreakdown(datasetId, measure, timeField, "5"));
        } catch (Exception e) { log.warn("contribution failed", e); }

        data.put("widgetTypes", Map.of(
                "available", Arrays.asList("KPI_CARD","TREND_CHART","COMPARISON_CHART","PIE_CHART","ANOMALY_TABLE","RCA_PANEL","FORECAST_CHART","DECISION_PANEL")
        ));
        return data;
    }

    private String getDefaultMeasure(Long datasetId) {
        return "value";
    }

    private String getDefaultTimeField(Long datasetId) {
        return "date";
    }

    private String generateReportSummary(Map<String, Object> sections) {
        StringBuilder sb = new StringBuilder("趋势分析报告摘要：");
        if (sections.containsKey("trendDiagnosis")) {
            sb.append("趋势诊断已完成。");
        }
        if (sections.containsKey("anomalies")) {
            sb.append("异常检测已完成。");
        }
        if (sections.containsKey("decisions")) {
            sb.append("决策建议已生成。");
        }
        return sb.toString();
    }

    private String getDefaultSections(String reportType) {
        List<String> defaultSections = Arrays.asList("overview", "trendDiagnosis", "comparison", "anomalies", "decisions");
        return safeJson(defaultSections);
    }

    private String safeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }
}
