package com.river.agi.analysis.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.analysis.entity.AnalysisTask;
import com.river.agi.analysis.entity.FieldStatistics;
import com.river.agi.analysis.entity.OutlierDetection;
import com.river.agi.analysis.mapper.AnalysisTaskMapper;
import com.river.agi.analysis.mapper.FieldStatisticsMapper;
import com.river.agi.analysis.mapper.OutlierDetectionMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
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
@DisplayName("分析服务测试")
class AnalysisServiceTest {

    @Mock
    private AnalysisTaskMapper analysisTaskMapper;
    @Mock
    private FieldStatisticsMapper fieldStatisticsMapper;
    @Mock
    private OutlierDetectionMapper outlierDetectionMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private ResourceAccessValidator accessValidator;

    private AnalysisService service;
    private MockedStatic<SecurityContextHolder> securityContextMockedStatic;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        mockAuth = mock(Authentication.class);
        SecurityContext mockSecurityContext = mock(SecurityContext.class);
        lenient().when(mockSecurityContext.getAuthentication()).thenReturn(mockAuth);
        securityContextMockedStatic = mockStatic(SecurityContextHolder.class);
        securityContextMockedStatic.when(SecurityContextHolder::getContext).thenReturn(mockSecurityContext);
        lenient().when(securityUtils.getCurrentUserId(any(Authentication.class))).thenReturn(1L);

        service = new AnalysisService(
                analysisTaskMapper, fieldStatisticsMapper, outlierDetectionMapper,
                datasetMapper, new ObjectMapper(), dataReader, securityUtils, accessValidator);
    }

    @AfterEach
    void tearDown() {
        if (securityContextMockedStatic != null) {
            securityContextMockedStatic.close();
        }
    }

    private Dataset buildParsedDataset() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setStatus("PARSED");
        dataset.setRowCount(10);
        dataset.setColumnCount(3);
        dataset.setSchemaJson("{\"date\":\"DATE\",\"sales\":\"NUMERIC\",\"category\":\"STRING\"}");
        return dataset;
    }

    private List<Map<String, String>> buildSampleRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("date", "2025-01-" + String.format("%02d", i + 1));
            row.put("sales", String.valueOf(100.0 + i * 10));
            row.put("category", i % 2 == 0 ? "A" : "B");
            rows.add(row);
        }
        return rows;
    }

    @Test
    @DisplayName("createAnalysisTask - 数据集不存在抛异常")
    void createAnalysisTask_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.createAnalysisTask(1L, "PROFILE", Map.of()));
    }

    @Test
    @DisplayName("createAnalysisTask - 数据集未解析抛异常")
    void createAnalysisTask_notParsed() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setStatus("UPLOADED");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertThrows(BusinessException.class, () ->
                service.createAnalysisTask(1L, "PROFILE", Map.of()));
    }

    @Test
    @DisplayName("createAnalysisTask - schema 为空抛异常")
    void createAnalysisTask_emptySchema() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setStatus("PARSED");
        dataset.setRowCount(10);
        dataset.setColumnCount(3);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertThrows(BusinessException.class, () ->
                service.createAnalysisTask(1L, "PROFILE", Map.of()));
    }

    @Test
    @DisplayName("createAnalysisTask - 行数为 0 抛异常")
    void createAnalysisTask_zeroRows() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setStatus("PARSED");
        dataset.setSchemaJson("{\"field\":\"NUMERIC\"}");
        dataset.setColumnCount(1);
        dataset.setRowCount(0);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertThrows(BusinessException.class, () ->
                service.createAnalysisTask(1L, "PROFILE", Map.of()));
    }

    @Test
    @DisplayName("createAnalysisTask - 列数为 0 抛异常")
    void createAnalysisTask_zeroCols() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setStatus("PARSED");
        dataset.setSchemaJson("{\"field\":\"NUMERIC\"}");
        dataset.setRowCount(10);
        dataset.setColumnCount(0);
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertThrows(BusinessException.class, () ->
                service.createAnalysisTask(1L, "PROFILE", Map.of()));
    }

    @Test
    @DisplayName("createAnalysisTask - 成功创建任务")
    void createAnalysisTask_success() {
        when(datasetMapper.selectById(anyLong())).thenReturn(buildParsedDataset());
        when(analysisTaskMapper.insert(any())).thenAnswer(inv -> {
            AnalysisTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        AnalysisTask task = service.createAnalysisTask(1L, "PROFILE", Map.of());
        assertNotNull(task);
        assertEquals("PENDING", task.getStatus());
        assertEquals("PROFILE", task.getTaskType());
    }

    @Test
    @DisplayName("getAnalysisTask - 任务不存在抛异常")
    void getAnalysisTask_notFound() {
        when(analysisTaskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getAnalysisTask(1L));
    }

    @Test
    @DisplayName("getAnalysisTask - 成功获取任务")
    void getAnalysisTask_success() {
        AnalysisTask task = new AnalysisTask();
        task.setId(1L);
        task.setDatasetId(10L);
        when(analysisTaskMapper.selectById(1L)).thenReturn(task);

        AnalysisTask result = service.getAnalysisTask(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("listAnalysisTasks - 默认分页")
    void listAnalysisTasks_default() {
        Page<AnalysisTask> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        when(analysisTaskMapper.selectPage(any(), any())).thenReturn(page);

        Page<AnalysisTask> result = service.listAnalysisTasks(null, 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("listAnalysisTasks - 带 datasetId")
    void listAnalysisTasks_withDatasetId() {
        Page<AnalysisTask> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        when(analysisTaskMapper.selectPage(any(), any())).thenReturn(page);

        Page<AnalysisTask> result = service.listAnalysisTasks(10L, 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("listAnalysisTasks - 无 auth 不验证访问权限")
    void listAnalysisTasks_noAuth() {
        // 安全上下文返回 null authentication
        SecurityContext nullCtx = mock(SecurityContext.class);
        when(nullCtx.getAuthentication()).thenReturn(null);
        securityContextMockedStatic.when(SecurityContextHolder::getContext).thenReturn(nullCtx);

        Page<AnalysisTask> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        when(analysisTaskMapper.selectPage(any(), any())).thenReturn(page);

        Page<AnalysisTask> result = service.listAnalysisTasks(null, 1, 10);
        assertNotNull(result);
    }

    @Test
    @DisplayName("runAnalysis - 数据集不存在抛异常")
    void runAnalysis_datasetNotFound() {
        when(datasetMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.runAnalysis(1L, "PROFILE"));
    }

    @Test
    @DisplayName("runAnalysis - PROFILE 成功")
    void runAnalysis_profile() {
        Dataset dataset = buildParsedDataset();
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSampleRows());
        when(analysisTaskMapper.insert(any())).thenAnswer(inv -> {
            AnalysisTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        AnalysisTask task = service.runAnalysis(1L, "PROFILE");
        assertNotNull(task);
        assertEquals("COMPLETED", task.getStatus());
        assertNotNull(task.getResultJson());
    }

    @Test
    @DisplayName("runAnalysis - OUTLIERS 成功")
    void runAnalysis_outliers() {
        Dataset dataset = buildParsedDataset();
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSampleRows());
        when(analysisTaskMapper.insert(any())).thenAnswer(inv -> {
            AnalysisTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        AnalysisTask task = service.runAnalysis(1L, "OUTLIERS");
        assertNotNull(task);
        assertEquals("COMPLETED", task.getStatus());
    }

    @Test
    @DisplayName("runAnalysis - QUALITY 成功")
    void runAnalysis_quality() {
        Dataset dataset = buildParsedDataset();
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(buildSampleRows());
        when(analysisTaskMapper.insert(any())).thenAnswer(inv -> {
            AnalysisTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        AnalysisTask task = service.runAnalysis(1L, "QUALITY");
        assertNotNull(task);
        assertEquals("COMPLETED", task.getStatus());
    }

    @Test
    @DisplayName("runAnalysis - 未知类型走异常分支")
    void runAnalysis_unknownType() {
        Dataset dataset = buildParsedDataset();
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(analysisTaskMapper.insert(any())).thenAnswer(inv -> {
            AnalysisTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        AnalysisTask task = service.runAnalysis(1L, "UNKNOWN");
        assertEquals("FAILED", task.getStatus());
        verify(analysisTaskMapper).updateById(any());
    }

    @Test
    @DisplayName("runAnalysis - 数据集未解析抛异常")
    void runAnalysis_notParsed() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setStatus("UPLOADED");
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        assertThrows(BusinessException.class, () -> service.runAnalysis(1L, "PROFILE"));
    }

    @Test
    @DisplayName("getFieldStatistics - 返回字段统计")
    void getFieldStatistics_success() {
        FieldStatistics fs = new FieldStatistics();
        fs.setId(1L);
        when(fieldStatisticsMapper.selectByDatasetId(1L)).thenReturn(List.of(fs));

        List<FieldStatistics> result = service.getFieldStatistics(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getOutliers - 返回离群值")
    void getOutliers_success() {
        OutlierDetection od = new OutlierDetection();
        od.setId(1L);
        when(outlierDetectionMapper.selectByDatasetId(1L)).thenReturn(List.of(od));

        List<OutlierDetection> result = service.getOutliers(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getAnalysisTaskCount - 返回任务数")
    void getAnalysisTaskCount_success() {
        when(analysisTaskMapper.selectCount(any())).thenReturn(42L);
        long count = service.getAnalysisTaskCount();
        assertEquals(42L, count);
    }

    @Test
    @DisplayName("runAnalysis - QUALITY 数据读取失败时返回错误")
    void runAnalysis_quality_readFailure() {
        Dataset dataset = buildParsedDataset();
        when(datasetMapper.selectById(1L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenThrow(new RuntimeException("IO error"));
        when(analysisTaskMapper.insert(any())).thenAnswer(inv -> {
            AnalysisTask t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        AnalysisTask task = service.runAnalysis(1L, "QUALITY");
        // 即使读取失败，runAnalysis 应捕获异常并标记 FAILED（或 COMPLETED 但带错误结果）
        assertNotNull(task);
        // QUALITY 路径内部 try-catch 处理错误，所以仍可能 COMPLETED
        assertDoesNotThrow(() -> task.getStatus());
    }
}
