package com.river.agi.chat.tool;

import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.service.PredictionService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("预测工具测试")
class PredictionToolsTest {

    @Mock private PredictionService predictionService;
    @Mock private ResourceAccessValidator accessValidator;
    @Mock private SecurityUtils securityUtils;
    @Mock private Authentication authentication;

    private PredictionTools tools;

    @BeforeEach
    void setUp() {
        tools = new PredictionTools(predictionService, accessValidator, securityUtils);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        doNothing().when(accessValidator).validateDatasetAccess(anyLong(), anyLong());
        doNothing().when(accessValidator).validatePredictionAccess(anyLong(), anyLong());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("创建预测任务 - 成功")
    void createPredictionTask_success() {
        PredictionTask created = new PredictionTask();
        created.setId(10L);
        created.setStatus("RUNNING");
        created.setModelVersionId(99L);
        when(predictionService.createPredictionTask(any(), any())).thenReturn(created);
        when(predictionService.runPrediction(10L)).thenReturn(created);

        String result = tools.createPredictionTask(1L, "sales", "date", 30, "AUTO", "0.95");

        assertTrue(result.contains("\"taskId\": 10"));
        assertTrue(result.contains("RUNNING"));
        verify(accessValidator).validateDatasetAccess(1L, 1L);
    }

    @Test
    @DisplayName("创建预测任务 - 异常返回错误 JSON")
    void createPredictionTask_exception() {
        when(predictionService.createPredictionTask(any(), any())).thenThrow(new RuntimeException("boom"));
        String result = tools.createPredictionTask(1L, "sales", "date", 30, "AUTO", "0.95");
        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("boom"));
    }

    @Test
    @DisplayName("获取预测结果 - 含模型指标与最新预测")
    void getPredictionResults_withMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(10L);
        task.setStatus("COMPLETED");
        task.setModelVersionId(99L);
        when(predictionService.getPredictionTask(10L)).thenReturn(task);

        ModelVersion mv = new ModelVersion();
        mv.setId(99L);
        mv.setMae(3.0);
        mv.setRmse(5.0);
        mv.setMape(4.0);
        mv.setTrainingMetricsJson("{\"r2\":0.9}");
        when(predictionService.getModelVersion(99L)).thenReturn(mv);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-01-01");
        pr.setPredictedValue(100.0);
        pr.setLowerBound(90.0);
        pr.setUpperBound(110.0);
        pr.setConfidence(0.95);
        when(predictionService.getPredictionResults(10L)).thenReturn(List.of(pr));

        String result = tools.getPredictionResults(10L);
        assertTrue(result.contains("\"modelMetrics\""));
        assertTrue(result.contains("\"latestPrediction\""));
        assertTrue(result.contains("\"predictionCount\": 1"));
    }

    @Test
    @DisplayName("获取预测结果 - 无模型版本与空结果")
    void getPredictionResults_noModelEmptyResults() {
        PredictionTask task = new PredictionTask();
        task.setId(10L);
        task.setStatus("PENDING");
        when(predictionService.getPredictionTask(10L)).thenReturn(task);
        when(predictionService.getPredictionResults(10L)).thenReturn(List.of());

        String result = tools.getPredictionResults(10L);
        assertTrue(result.contains("\"predictionCount\": 0"));
        assertFalse(result.contains("modelMetrics"));
    }

    @Test
    @DisplayName("获取预测结果 - 异常返回错误 JSON")
    void getPredictionResults_exception() {
        when(predictionService.getPredictionTask(anyLong())).thenThrow(new RuntimeException("nope"));
        String result = tools.getPredictionResults(10L);
        assertTrue(result.contains("\"error\""));
    }

    @Test
    @DisplayName("重训预测 - 成功")
    void retrainPrediction_success() {
        PredictionTask task = new PredictionTask();
        task.setId(10L);
        task.setStatus("RETRAINING");
        when(predictionService.retrainPrediction(10L)).thenReturn(task);
        String result = tools.retrainPrediction(10L);
        assertTrue(result.contains("\"taskId\": 10"));
        assertTrue(result.contains("RETRAINING"));
        verify(accessValidator).validatePredictionAccess(10L, 1L);
    }

    @Test
    @DisplayName("重训预测 - 异常返回错误 JSON")
    void retrainPrediction_exception() {
        when(predictionService.retrainPrediction(anyLong())).thenThrow(new RuntimeException("fail"));
        String result = tools.retrainPrediction(10L);
        assertTrue(result.contains("\"error\""));
    }
}
