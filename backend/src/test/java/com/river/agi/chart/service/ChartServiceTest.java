package com.river.agi.chart.service;

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
import com.river.agi.dataset.service.LocalStorageService;
import com.river.agi.security.entity.SecurityScanTask;
import com.river.agi.security.mapper.SecurityScanTaskMapper;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("图表报告服务测试")
class ChartServiceTest {
    
    @Mock
    private ChartConfigMapper chartConfigMapper;
    
    @Mock
    private ReportMapper reportMapper;
    
    @Mock
    private DatasetMapper datasetMapper;
    
    @Mock
    private AnalysisTaskMapper analysisTaskMapper;
    
    @Mock
    private SecurityScanTaskMapper securityScanTaskMapper;
    
    @Mock
    private PredictionTaskMapper predictionTaskMapper;
    
    @Mock
    private PredictionResultMapper predictionResultMapper;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private ResourceAccessValidator accessValidator;
    
    private ChartService chartService;
    private ObjectMapper objectMapper;
    private DatasetDataReaderService dataReader;
    private LocalStorageService localStorageService;
    private Authentication mockAuth;
    private MockedStatic<SecurityContextHolder> securityContextMockedStatic;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        localStorageService = new LocalStorageService();
        dataReader = new DatasetDataReaderService(localStorageService, objectMapper);

        mockAuth = mock(Authentication.class);
        SecurityContext mockSecurityContext = mock(SecurityContext.class);
        lenient().when(mockSecurityContext.getAuthentication()).thenReturn(mockAuth);
        securityContextMockedStatic = mockStatic(SecurityContextHolder.class);
        securityContextMockedStatic.when(SecurityContextHolder::getContext).thenReturn(mockSecurityContext);
        lenient().when(securityUtils.getCurrentUserId(any(Authentication.class))).thenReturn(1L);
        
        chartService = new ChartService(
            chartConfigMapper,
            reportMapper,
            datasetMapper,
            objectMapper,
            dataReader,
            analysisTaskMapper,
            securityScanTaskMapper,
            predictionTaskMapper,
            predictionResultMapper,
            securityUtils,
            accessValidator
        );
    }

    @AfterEach
    void tearDown() {
        if (securityContextMockedStatic != null) {
            securityContextMockedStatic.close();
        }
    }
    
    @Test
    @DisplayName("推荐图表 - 数据集不存在")
    void recommendCharts_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        
        assertThrows(Exception.class, () -> 
            chartService.recommendCharts(1L)
        );
    }
    
    @Test
    @DisplayName("推荐图表 - 成功推荐")
    void recommendCharts_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setStatus("PARSED");
        dataset.setRowCount(100);
        dataset.setColumnCount(3);
        dataset.setSchemaJson("{\"date\":\"STRING\",\"sales\":\"NUMERIC\",\"category\":\"STRING\"}");
        
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        
        List<Map<String, Object>> recommendations = chartService.recommendCharts(1L);
        
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
    }
    
    @Test
    @DisplayName("生成报告 - 数据集不存在")
    void generateReport_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        
        assertThrows(Exception.class, () -> 
            chartService.generateReport(1L, "FULL", mockAuth)
        );
    }
    
    @Test
    @DisplayName("生成报告 - 成功生成")
    void generateReport_success() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test_dataset");
        dataset.setStatus("PARSED");
        dataset.setRowCount(1000);
        dataset.setColumnCount(5);
        dataset.setSchemaJson("{\"field1\":\"STRING\",\"field2\":\"NUMERIC\"}");
        
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        when(reportMapper.insert(any())).thenReturn(1);
        when(analysisTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(predictionTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        
        Report report = chartService.generateReport(1L, "FULL", mockAuth);
        
        assertNotNull(report);
        assertEquals("数据报告 - test_dataset", report.getTitle());
    }
    
    @Test
    @DisplayName("生成报告 - 包含质量分析结果")
    void generateReport_withQualityAnalysis() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test_dataset");
        dataset.setStatus("PARSED");
        dataset.setRowCount(500);
        dataset.setColumnCount(3);
        dataset.setSchemaJson("{\"field1\":\"STRING\",\"field2\":\"NUMERIC\"}");
        
        AnalysisTask qualityTask = new AnalysisTask();
        qualityTask.setId(1L);
        qualityTask.setStatus("COMPLETED");
        qualityTask.setResultJson("{\"overallScore\":0.85,\"columnIssues\":[{\"nullRate\":0.15}]}");
        
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        when(reportMapper.insert(any())).thenReturn(1);
        when(analysisTaskMapper.selectList(any())).thenReturn(List.of(qualityTask));
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(predictionTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        
        Report report = chartService.generateReport(1L, "FULL", mockAuth);
        
        assertNotNull(report);
    }
    
    @Test
    @DisplayName("生成报告 - 包含安全扫描结果")
    void generateReport_withSecurityScan() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("test_dataset");
        dataset.setStatus("PARSED");
        dataset.setRowCount(200);
        dataset.setColumnCount(4);
        dataset.setSchemaJson("{\"field1\":\"STRING\",\"field2\":\"NUMERIC\"}");
        
        SecurityScanTask scanTask = new SecurityScanTask();
        scanTask.setId(1L);
        scanTask.setStatus("COMPLETED");
        scanTask.setTotalFields(4);
        scanTask.setSensitiveFieldsFound(2);
        scanTask.setHighRiskCount(1);
        scanTask.setMediumRiskCount(1);
        
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        when(reportMapper.insert(any())).thenReturn(1);
        when(analysisTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(List.of(scanTask));
        when(predictionTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        
        Report report = chartService.generateReport(1L, "FULL", mockAuth);
        
        assertNotNull(report);
    }
    
    @Test
    @DisplayName("生成报告 - 包含预测分析结果")
    void generateReport_withPrediction() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setName("sales_dataset");
        dataset.setStatus("PARSED");
        dataset.setRowCount(500);
        dataset.setColumnCount(5);
        dataset.setSchemaJson("{\"field1\":\"STRING\",\"field2\":\"NUMERIC\"}");
        
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(1L);
        task.setStatus("COMPLETED");
        task.setTargetField("sales");
        task.setModelType("HOLT_WINTERS");
        
        PredictionResult r1 = new PredictionResult();
        r1.setPredictionDate("2026-01-01");
        r1.setPredictedValue(1000.0);
        r1.setConfidence(0.9);
        
        PredictionResult r2 = new PredictionResult();
        r2.setPredictionDate("2026-01-02");
        r2.setPredictedValue(1100.0);
        r2.setConfidence(0.92);
        
        when(datasetMapper.selectById(anyLong())).thenReturn(dataset);
        when(reportMapper.insert(any())).thenReturn(1);
        when(analysisTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(securityScanTaskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(predictionTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(predictionResultMapper.selectByTaskId(anyLong())).thenReturn(List.of(r1, r2));
        
        Report report = chartService.generateReport(1L, "FULL", mockAuth);
        
        assertNotNull(report);
        String content = report.getContent();
        assertNotNull(content);
        Map<String, Object> parsed = new ObjectMapper().readValue(content, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        assertNotNull(parsed.get("sections"));
    }
    
    @Test
    @DisplayName("获取报告列表 - 分页返回")
    void getReports_withPagination() {
        Page<Report> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        
        when(reportMapper.selectPage(any(), any())).thenReturn(page);
        
        var result = chartService.getReports(1, 10, null);
        
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("获取单个报告")
    void getReport_found() {
        Report report = new Report();
        report.setId(1L);
        report.setTitle("Test Report");
        
        when(reportMapper.selectById(anyLong())).thenReturn(report);
        
        Report result = chartService.getReport(1L);
        
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }
    
    @Test
    @DisplayName("获取单个报告 - 不存在")
    void getReport_notFound() {
        when(reportMapper.selectById(anyLong())).thenReturn(null);
        
        assertThrows(Exception.class, () -> chartService.getReport(1L));
    }
    
    @Test
    @DisplayName("删除报告")
    void deleteReport_success() {
        when(reportMapper.deleteById(anyLong())).thenReturn(1);
        
        assertDoesNotThrow(() -> chartService.deleteReport(1L));
    }
    
    @Test
    @DisplayName("保存图表配置")
    void saveChartConfig_success() {
        when(chartConfigMapper.insert(any())).thenReturn(1);
        
        ChartConfig config = chartService.saveChartConfig(
            1L, "BAR", "Test Chart", "category", "value", null, null
        );
        
        assertNotNull(config);
    }
    
    @Test
    @DisplayName("获取图表配置列表")
    void getChartConfigs_success() {
        when(chartConfigMapper.selectByDatasetId(anyLong())).thenReturn(new ArrayList<>());
        
        List<ChartConfig> configs = chartService.getChartConfigs(1L);
        
        assertNotNull(configs);
    }
}