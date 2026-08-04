package com.river.agi.chat.tool;

import com.river.agi.analysis.entity.AnalysisTask;
import com.river.agi.analysis.service.AnalysisService;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("分析工具测试")
class AnalysisToolsTest {

    @Mock private AnalysisService analysisService;
    @Mock private DatasetMapper datasetMapper;
    @Mock private ResourceAccessValidator accessValidator;
    @Mock private SecurityUtils securityUtils;
    @Mock private Authentication authentication;

    private AnalysisTools tools;

    @BeforeEach
    void setUp() {
        tools = new AnalysisTools(analysisService, datasetMapper, accessValidator, securityUtils);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        doNothing().when(accessValidator).validateDatasetAccess(anyLong(), anyLong());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Dataset dataset() {
        Dataset d = new Dataset();
        d.setId(1L);
        d.setName("ds");
        d.setRowCount(100);
        d.setColumnCount(5);
        d.setStatus("PARSED");
        d.setFileType("CSV");
        d.setDescription("desc");
        return d;
    }

    @Test
    @DisplayName("检查数据集 - 找到")
    void inspectDataset_found() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset());
        Map<String, Object> result = tools.inspectDataset(1L);
        assertEquals(1L, result.get("id"));
        assertEquals("ds", result.get("name"));
        assertEquals(100, result.get("rowCount"));
        verify(accessValidator).validateDatasetAccess(1L, 1L);
    }

    @Test
    @DisplayName("检查数据集 - 未找到")
    void inspectDataset_notFound() {
        when(datasetMapper.selectById(1L)).thenReturn(null);
        Map<String, Object> result = tools.inspectDataset(1L);
        assertEquals("Dataset not found", result.get("error"));
    }

    @Test
    @DisplayName("检查数据集 - 异常返回错误")
    void inspectDataset_exception() {
        when(datasetMapper.selectById(anyLong())).thenThrow(new RuntimeException("boom"));
        Map<String, Object> result = tools.inspectDataset(1L);
        assertEquals("boom", result.get("error"));
    }

    @Test
    @DisplayName("数据画像 - 有结果 JSON")
    void profileDataset_withResult() {
        AnalysisTask at = new AnalysisTask();
        at.setResultJson("{\"profile\":true}");
        when(analysisService.runAnalysis(1L, "PROFILE")).thenReturn(at);
        assertEquals("{\"profile\":true}", tools.profileDataset(1L));
    }

    @Test
    @DisplayName("数据画像 - 结果为空返回默认")
    void profileDataset_emptyResult() {
        AnalysisTask at = new AnalysisTask();
        when(analysisService.runAnalysis(1L, "PROFILE")).thenReturn(at);
        String result = tools.profileDataset(1L);
        assertTrue(result.contains("completed"));
    }

    @Test
    @DisplayName("数据画像 - 异常返回错误 JSON")
    void profileDataset_exception() {
        when(analysisService.runAnalysis(anyLong(), anyString())).thenThrow(new RuntimeException("err"));
        String result = tools.profileDataset(1L);
        assertTrue(result.contains("\"error\""));
    }

    @Test
    @DisplayName("异常检测 - 有结果 JSON")
    void detectOutliers_withResult() {
        AnalysisTask at = new AnalysisTask();
        at.setResultJson("{\"outliers\":1}");
        when(analysisService.runAnalysis(1L, "OUTLIERS")).thenReturn(at);
        assertEquals("{\"outliers\":1}", tools.detectOutliers(1L, "sales"));
    }

    @Test
    @DisplayName("异常检测 - 结果为空返回默认")
    void detectOutliers_emptyResult() {
        when(analysisService.runAnalysis(1L, "OUTLIERS")).thenReturn(null);
        String result = tools.detectOutliers(1L, "sales");
        assertTrue(result.contains("completed"));
    }

    @Test
    @DisplayName("异常检测 - 异常返回错误 JSON")
    void detectOutliers_exception() {
        when(analysisService.runAnalysis(anyLong(), anyString())).thenThrow(new RuntimeException("err"));
        String result = tools.detectOutliers(1L, "sales");
        assertTrue(result.contains("\"error\""));
    }

    @Test
    @DisplayName("质量分析 - 有结果 JSON")
    void analyzeQuality_withResult() {
        AnalysisTask at = new AnalysisTask();
        at.setResultJson("{\"quality\":\"A\"}");
        when(analysisService.runAnalysis(1L, "QUALITY")).thenReturn(at);
        assertEquals("{\"quality\":\"A\"}", tools.analyzeQuality(1L));
    }

    @Test
    @DisplayName("质量分析 - 结果为空返回默认")
    void analyzeQuality_emptyResult() {
        when(analysisService.runAnalysis(1L, "QUALITY")).thenReturn(null);
        String result = tools.analyzeQuality(1L);
        assertTrue(result.contains("completed"));
    }

    @Test
    @DisplayName("质量分析 - 异常返回错误 JSON")
    void analyzeQuality_exception() {
        when(analysisService.runAnalysis(anyLong(), anyString())).thenThrow(new RuntimeException("err"));
        String result = tools.analyzeQuality(1L);
        assertTrue(result.contains("\"error\""));
    }
}
