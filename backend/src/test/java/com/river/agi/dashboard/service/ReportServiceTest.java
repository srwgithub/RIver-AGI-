package com.river.agi.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dashboard.entity.ReportInstance;
import com.river.agi.dashboard.entity.ReportTemplate;
import com.river.agi.dashboard.mapper.ReportInstanceMapper;
import com.river.agi.dashboard.mapper.ReportTemplateMapper;
import com.river.agi.trend.service.AnomalyDetectionService;
import com.river.agi.trend.service.ComparisonAnalysisService;
import com.river.agi.trend.service.DecisionSupportService;
import com.river.agi.trend.service.OlapAnalysisService;
import com.river.agi.trend.service.RootCauseAnalysisService;
import com.river.agi.trend.service.TrendDiagnosisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("报告服务测试")
class ReportServiceTest {

    @Mock
    private ReportTemplateMapper templateMapper;
    @Mock
    private ReportInstanceMapper instanceMapper;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private TrendDiagnosisService trendDiagnosisService;
    @Mock
    private ComparisonAnalysisService comparisonService;
    @Mock
    private AnomalyDetectionService anomalyService;
    @Mock
    private RootCauseAnalysisService rcaService;
    @Mock
    private OlapAnalysisService olapService;
    @Mock
    private DecisionSupportService decisionService;
    @Mock
    private Authentication auth;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(
                templateMapper, instanceMapper, securityUtils, new ObjectMapper(),
                trendDiagnosisService, comparisonService, anomalyService,
                rcaService, olapService, decisionService);
        lenient().when(securityUtils.getCurrentUserId(any(Authentication.class))).thenReturn(1L);
    }

    @Test
    @DisplayName("listTemplates - 返回模板列表")
    void listTemplates_success() {
        ReportTemplate t = new ReportTemplate();
        t.setId(1L);
        when(templateMapper.selectList(any())).thenReturn(List.of(t));
        List<ReportTemplate> result = service.listTemplates(null, null);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listTemplates - 带 datasetId 和 reportType")
    void listTemplates_withFilters() {
        when(templateMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<ReportTemplate> result = service.listTemplates(10L, "TREND");
        assertNotNull(result);
    }

    @Test
    @DisplayName("createTemplate - 默认 sectionsJson 填充")
    void createTemplate_defaultSections() {
        ReportTemplate t = new ReportTemplate();
        t.setName("test");
        t.setReportType("TREND");
        when(templateMapper.insert(any())).thenAnswer(inv -> {
            ReportTemplate inserted = inv.getArgument(0);
            inserted.setId(1L);
            return 1;
        });

        ReportTemplate result = service.createTemplate(t, auth);
        assertNotNull(result);
        assertEquals(1L, result.getTenantId());
        assertEquals(1L, result.getCreatedBy());
        assertEquals(0, result.getDeleted());
        assertNotNull(result.getSectionsJson());
        assertEquals("{}", result.getParametersJson());
    }

    @Test
    @DisplayName("createTemplate - 已有 sectionsJson 保留")
    void createTemplate_existingSections() {
        ReportTemplate t = new ReportTemplate();
        t.setName("test");
        t.setReportType("TREND");
        t.setSectionsJson("[\"custom\"]");
        t.setParametersJson("{\"k\":\"v\"}");
        when(templateMapper.insert(any())).thenAnswer(inv -> {
            ReportTemplate inserted = inv.getArgument(0);
            inserted.setId(1L);
            return 1;
        });

        ReportTemplate result = service.createTemplate(t, auth);
        assertEquals("[\"custom\"]", result.getSectionsJson());
        assertEquals("{\"k\":\"v\"}", result.getParametersJson());
    }

    @Test
    @DisplayName("updateTemplate - 模板不存在抛异常")
    void updateTemplate_notFound() {
        when(templateMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.updateTemplate(1L, new ReportTemplate(), auth));
    }

    @Test
    @DisplayName("updateTemplate - 模板已删除抛异常")
    void updateTemplate_deleted() {
        ReportTemplate t = new ReportTemplate();
        t.setDeleted(1);
        when(templateMapper.selectById(1L)).thenReturn(t);
        assertThrows(BusinessException.class, () ->
                service.updateTemplate(1L, new ReportTemplate(), auth));
    }

    @Test
    @DisplayName("updateTemplate - 成功更新")
    void updateTemplate_success() {
        ReportTemplate existing = new ReportTemplate();
        existing.setId(1L);
        existing.setDeleted(0);
        when(templateMapper.selectById(1L)).thenReturn(existing);
        when(templateMapper.updateById(any())).thenReturn(1);

        ReportTemplate updates = new ReportTemplate();
        updates.setName("new name");
        updates.setDescription("desc");
        updates.setSectionsJson("{}");
        updates.setParametersJson("{}");

        ReportTemplate result = service.updateTemplate(1L, updates, auth);
        assertEquals("new name", result.getName());
        assertEquals("desc", result.getDescription());
    }

    @Test
    @DisplayName("deleteTemplate - 模板不存在直接返回")
    void deleteTemplate_notFound() {
        when(templateMapper.selectById(anyLong())).thenReturn(null);
        assertDoesNotThrow(() -> service.deleteTemplate(1L));
        verify(templateMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("deleteTemplate - 成功删除")
    void deleteTemplate_success() {
        ReportTemplate t = new ReportTemplate();
        t.setId(1L);
        when(templateMapper.selectById(1L)).thenReturn(t);
        when(templateMapper.updateById(any())).thenReturn(1);
        assertDoesNotThrow(() -> service.deleteTemplate(1L));
        verify(templateMapper).updateById(any());
    }

    @Test
    @DisplayName("generateReport - 不带 predictionTaskId")
    void generateReport_noPredictionTask() {
        when(olapService.getKpiDashboard(anyLong(), anyString(), anyString())).thenReturn(Map.of("total", 100.0));
        when(decisionService.getDecisionRecommendations(anyLong(), anyString())).thenReturn(Map.of("overallTrend", "上升"));
        when(instanceMapper.insert(any())).thenAnswer(inv -> {
            ReportInstance ri = inv.getArgument(0);
            ri.setId(1L);
            return 1;
        });

        ReportInstance result = service.generateReport(null, 10L, null, "JSON", auth);
        assertNotNull(result);
        assertEquals("GENERATED", result.getStatus());
        assertEquals("JSON", result.getExportFormat());
        assertEquals(1L, result.getTenantId());
        assertNotNull(result.getContentJson());
    }

    @Test
    @DisplayName("generateReport - 带 predictionTaskId")
    void generateReport_withPredictionTask() {
        ReportTemplate template = new ReportTemplate();
        template.setId(1L);
        template.setName("test template");
        when(templateMapper.selectById(1L)).thenReturn(template);
        when(olapService.getKpiDashboard(anyLong(), anyString(), anyString())).thenReturn(Map.of("total", 100.0));
        when(trendDiagnosisService.diagnose(anyLong())).thenReturn(null);
        when(comparisonService.getActualVsPredicted(anyLong())).thenReturn(Map.of("summary", "data"));
        when(anomalyService.getAlerts(anyLong(), anyLong(), any(), any())).thenReturn(new ArrayList<>());
        when(anomalyService.getAlertSummary(anyLong())).thenReturn(Map.of("totalAlerts", 0));
        when(decisionService.getDecisionRecommendations(anyLong(), anyString())).thenReturn(Map.of("overallTrend", "上升"));
        when(instanceMapper.insert(any())).thenAnswer(inv -> {
            ReportInstance ri = inv.getArgument(0);
            ri.setId(1L);
            return 1;
        });

        ReportInstance result = service.generateReport(1L, 10L, 5L, null, auth);
        assertNotNull(result);
        assertNotNull(result.getTitle());
        assertTrue(result.getTitle().contains("test template"));
        assertEquals("JSON", result.getExportFormat());
    }

    @Test
    @DisplayName("generateReport - KPI 失败但其他部分继续执行")
    void generateReport_kpiFailure() {
        when(olapService.getKpiDashboard(anyLong(), anyString(), anyString())).thenThrow(new RuntimeException("KPI failed"));
        when(decisionService.getDecisionRecommendations(anyLong(), anyString())).thenThrow(new RuntimeException("decision failed"));
        when(instanceMapper.insert(any())).thenAnswer(inv -> {
            ReportInstance ri = inv.getArgument(0);
            ri.setId(1L);
            return 1;
        });

        ReportInstance result = service.generateReport(null, 10L, null, "JSON", auth);
        assertNotNull(result);
        assertEquals("GENERATED", result.getStatus());
    }

    @Test
    @DisplayName("getReportInstance - 找到实例")
    void getReportInstance_found() {
        ReportInstance ri = new ReportInstance();
        ri.setId(1L);
        ri.setDeleted(0);
        when(instanceMapper.selectById(1L)).thenReturn(ri);
        ReportInstance result = service.getReportInstance(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getReportInstance - 不存在抛异常")
    void getReportInstance_notFound() {
        when(instanceMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getReportInstance(1L));
    }

    @Test
    @DisplayName("getReportInstance - 已删除抛异常")
    void getReportInstance_deleted() {
        ReportInstance ri = new ReportInstance();
        ri.setDeleted(1);
        when(instanceMapper.selectById(1L)).thenReturn(ri);
        assertThrows(BusinessException.class, () -> service.getReportInstance(1L));
    }

    @Test
    @DisplayName("listReportInstances - 返回实例列表")
    void listReportInstances_success() {
        ReportInstance ri = new ReportInstance();
        ri.setId(1L);
        when(instanceMapper.selectList(any())).thenReturn(List.of(ri));
        List<ReportInstance> result = service.listReportInstances(10L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listReportInstances - datasetId 为 null")
    void listReportInstances_nullDataset() {
        when(instanceMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<ReportInstance> result = service.listReportInstances(null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getDefaultTrendDashboardData - 简单调用")
    void getDefaultTrendDashboardData_simple() {
        when(olapService.getKpiDashboard(anyLong(), anyString(), anyString())).thenReturn(Map.of("total", 100.0));
        when(decisionService.getDecisionRecommendations(anyLong(), anyString())).thenReturn(Map.of("overallTrend", "上升"));
        when(rcaService.getContributionBreakdown(anyLong(), anyString(), anyString(), anyString())).thenReturn(new ArrayList<>());

        Map<String, Object> data = service.getDefaultTrendDashboardData(10L, null);
        assertNotNull(data);
        assertNotNull(data.get("kpi"));
        assertNotNull(data.get("widgetTypes"));
    }

    @Test
    @DisplayName("getDefaultTrendDashboardData - 带 predictionTaskId")
    void getDefaultTrendDashboardData_withTask() {
        when(olapService.getKpiDashboard(anyLong(), anyString(), anyString())).thenReturn(Map.of("total", 100.0));
        when(trendDiagnosisService.getLatestDiagnosis(anyLong())).thenReturn(null);
        when(comparisonService.getActualVsPredicted(anyLong())).thenReturn(Map.of("summary", "data"));
        when(anomalyService.getAlertSummary(anyLong())).thenReturn(Map.of("totalAlerts", 0));
        when(decisionService.generateThreeScenarios(anyLong())).thenReturn(Map.of("optimistic", "x"));
        when(decisionService.getDecisionRecommendations(anyLong(), anyString())).thenReturn(Map.of("overallTrend", "上升"));
        when(rcaService.getContributionBreakdown(anyLong(), anyString(), anyString(), anyString())).thenReturn(new ArrayList<>());

        Map<String, Object> data = service.getDefaultTrendDashboardData(10L, 5L);
        assertNotNull(data);
        // trendDiagnosis 可能为 null（当 getLatestDiagnosis 返回 null 时）
        assertTrue(data.containsKey("trendDiagnosis"));
        assertNotNull(data.get("actualVsPredicted"));
        assertNotNull(data.get("alertSummary"));
        assertNotNull(data.get("scenarios"));
    }

    @Test
    @DisplayName("getDefaultTrendDashboardData - 带 measure 和 timeField 参数")
    void getDefaultTrendDashboardData_withMeasureAndTime() {
        when(olapService.getKpiDashboard(anyLong(), eq("sales"), eq("date"))).thenReturn(Map.of("total", 100.0));
        when(decisionService.getDecisionRecommendations(anyLong(), eq("sales"))).thenReturn(Map.of("overallTrend", "上升"));
        when(rcaService.getContributionBreakdown(anyLong(), eq("sales"), eq("date"), anyString())).thenReturn(new ArrayList<>());

        Map<String, Object> data = service.getDefaultTrendDashboardData(10L, null, "sales", "date");
        assertNotNull(data);
    }

    @Test
    @DisplayName("getDefaultTrendDashboardData - 异常情况优雅降级")
    void getDefaultTrendDashboardData_gracefulFailure() {
        when(olapService.getKpiDashboard(anyLong(), anyString(), anyString())).thenThrow(new RuntimeException("KPI failed"));
        when(decisionService.getDecisionRecommendations(anyLong(), anyString())).thenThrow(new RuntimeException("decision failed"));
        when(rcaService.getContributionBreakdown(anyLong(), anyString(), anyString(), anyString())).thenThrow(new RuntimeException("rca failed"));

        Map<String, Object> data = service.getDefaultTrendDashboardData(10L, null);
        assertNotNull(data);
        // 即使失败也应该有错误信息和 widgetTypes
        assertNotNull(data.get("kpi"));
        assertNotNull(data.get("widgetTypes"));
    }
}
