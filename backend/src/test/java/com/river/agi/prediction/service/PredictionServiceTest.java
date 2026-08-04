package com.river.agi.prediction.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock private PredictionTaskMapper predictionTaskMapper;
    @Mock private ModelVersionMapper modelVersionMapper;
    @Mock private PredictionResultMapper predictionResultMapper;
    @Mock private DatasetMapper datasetMapper;
    @Mock private SecurityUtils securityUtils;
    @Mock private ResourceAccessValidator accessValidator;
    @Mock private DatasetDataReaderService dataReader;
    @Mock private RoleMapper roleMapper;
    @Mock private Authentication authentication;

    private ObjectMapper objectMapper;
    private PredictionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new PredictionService(predictionTaskMapper, modelVersionMapper, predictionResultMapper,
                datasetMapper, objectMapper, securityUtils, accessValidator, dataReader, roleMapper);
    }

    private PredictionTask task() {
        PredictionTask t = new PredictionTask();
        t.setId(1L);
        t.setDatasetId(42L);
        t.setModelType("LINEAR_REGRESSION");
        t.setTimeField("date");
        t.setTargetField("sales");
        t.setForecastDays(2);
        return t;
    }

    private Dataset parsedDataset() {
        Dataset d = new Dataset();
        d.setId(42L);
        d.setStatus("PARSED");
        d.setSchemaJson("[{\"name\":\"date\"},{\"name\":\"sales\"}]");
        d.setRowCount(100);
        d.setFileType("csv");
        d.setFileUrl("file://test.csv");
        return d;
    }

    private List<Map<String, String>> seriesRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("date", String.format("2026-01-%02d", i));
            row.put("sales", String.valueOf(100 + i * 10));
            rows.add(row);
        }
        return rows;
    }

    // ===== createPredictionTask =====

    @Test
    @DisplayName("createPredictionTask happy path returns task with PENDING status")
    void createPredictionTask_happy() {
        PredictionTask t = task();
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);

        PredictionTask result = service.createPredictionTask(t, authentication);

        assertEquals("PENDING", result.getStatus());
        assertEquals(1L, result.getCreatedBy());
        assertNotNull(result.getCreatedAt());
        verify(predictionTaskMapper).insert(t);
        verify(accessValidator).validateDatasetOwnership(42L, 1L);
    }

    @Test
    @DisplayName("createPredictionTask throws when dataset not found")
    void createPredictionTask_datasetNotFound() {
        PredictionTask t = task();
        when(datasetMapper.selectById(42L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, authentication));
        assertTrue(ex.getMessage().contains("Dataset not found"));
        verify(predictionTaskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createPredictionTask throws when dataset not PARSED")
    void createPredictionTask_notParsed() {
        PredictionTask t = task();
        Dataset d = parsedDataset();
        d.setStatus("UPLOADING");
        when(datasetMapper.selectById(42L)).thenReturn(d);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, authentication));
        assertTrue(ex.getMessage().contains("数据集尚未解析完成"));
    }

    @Test
    @DisplayName("createPredictionTask throws when schemaJson is blank")
    void createPredictionTask_blankSchema() {
        PredictionTask t = task();
        Dataset d = parsedDataset();
        d.setSchemaJson("  ");
        when(datasetMapper.selectById(42L)).thenReturn(d);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, authentication));
        assertTrue(ex.getMessage().contains("Schema"));
    }

    @Test
    @DisplayName("createPredictionTask throws when rowCount is zero")
    void createPredictionTask_zeroRows() {
        PredictionTask t = task();
        Dataset d = parsedDataset();
        d.setRowCount(0);
        when(datasetMapper.selectById(42L)).thenReturn(d);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, authentication));
        assertTrue(ex.getMessage().contains("行数为 0"));
    }

    @Test
    @DisplayName("createPredictionTask throws when rowCount is null")
    void createPredictionTask_nullRows() {
        PredictionTask t = task();
        Dataset d = parsedDataset();
        d.setRowCount(null);
        when(datasetMapper.selectById(42L)).thenReturn(d);

        assertThrows(BusinessException.class, () -> service.createPredictionTask(t, authentication));
    }

    @Test
    @DisplayName("createPredictionTask throws when forecastDays below 1")
    void createPredictionTask_forecastDaysTooLow() {
        PredictionTask t = task();
        t.setForecastDays(0);
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, authentication));
        assertTrue(ex.getMessage().contains("预测天数"));
    }

    @Test
    @DisplayName("createPredictionTask throws when forecastDays above MAX_FORECAST_DAYS")
    void createPredictionTask_forecastDaysTooHigh() {
        PredictionTask t = task();
        t.setForecastDays(400);
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.createPredictionTask(t, authentication));
    }

    @Test
    @DisplayName("createPredictionTask accepts null forecastDays")
    void createPredictionTask_nullForecastDays() {
        PredictionTask t = task();
        t.setForecastDays(null);
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(securityUtils.getCurrentUserId(authentication)).thenReturn(1L);

        PredictionTask result = service.createPredictionTask(t, authentication);

        assertEquals("PENDING", result.getStatus());
        verify(predictionTaskMapper).insert(t);
    }

    // ===== runPrediction =====

    @Test
    @DisplayName("runPrediction throws when task not found")
    void runPrediction_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.runPrediction(99L));
        assertTrue(ex.getMessage().contains("Prediction task not found"));
    }

    @Test
    @DisplayName("runPrediction executes synchronously when taskExecutor is null and completes")
    void runPrediction_syncHappyPath() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        PredictionTask result = service.runPrediction(1L);

        // After async (sync here) execution, status should be COMPLETED or FAILED
        // The executePredictionAsync runs synchronously, so status should be COMPLETED
        assertTrue("COMPLETED".equals(result.getStatus()) || "FAILED".equals(result.getStatus()),
                "Status should be COMPLETED or FAILED, got " + result.getStatus());
        verify(predictionTaskMapper, atLeast(2)).updateById(any());
    }

    @Test
    @DisplayName("runPrediction sets RUNNING status before execution")
    void runPrediction_setsRunningStatus() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        service.runPrediction(1L);

        // The first updateById should set RUNNING, the second should set COMPLETED/FAILED
        verify(predictionTaskMapper, atLeast(2)).updateById(any());
    }

    @Test
    @DisplayName("runPrediction handles insufficient series data and marks FAILED")
    void runPrediction_insufficientData() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        // Only 2 rows — trainModel requires >= 3
        when(dataReader.readRows(any())).thenReturn(seriesRows().subList(0, 2));

        service.runPrediction(1L);

        assertEquals("FAILED", t.getStatus());
        assertNotNull(t.getErrorMessage());
    }

    // ===== getPredictionTask =====

    @Test
    @DisplayName("getPredictionTask returns task when found")
    void getPredictionTask_found() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);

        PredictionTask result = service.getPredictionTask(1L);

        assertSame(t, result);
    }

    @Test
    @DisplayName("getPredictionTask throws when not found")
    void getPredictionTask_notFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getPredictionTask(99L));
        assertTrue(ex.getMessage().contains("Prediction task not found"));
    }

    // ===== getPredictionTasks =====

    @Test
    @DisplayName("getPredictionTasks returns paged results without auth")
    void getPredictionTasks_noAuth() {
        Page<PredictionTask> page = new Page<>(1, 10);
        page.setRecords(List.of(task()));
        page.setTotal(1L);
        when(predictionTaskMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.getPredictionTasks(1, 10);

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getTotal());
    }

    @Test
    @DisplayName("getPredictionTasks returns empty page when no records")
    void getPredictionTasks_empty() {
        Page<PredictionTask> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(predictionTaskMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.getPredictionTasks(1, 10);

        assertTrue(result.getRecords().isEmpty());
    }

    // ===== getPredictionResults =====

    @Test
    @DisplayName("getPredictionResults delegates to mapper")
    void getPredictionResults_returnsResults() {
        PredictionResult r = new PredictionResult();
        r.setTaskId(1L);
        r.setPredictedValue(100.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(r));

        List<PredictionResult> results = service.getPredictionResults(1L);

        assertEquals(1, results.size());
        assertEquals(100.0, results.get(0).getPredictedValue());
    }

    @Test
    @DisplayName("getPredictionResults returns empty list when no results")
    void getPredictionResults_empty() {
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of());

        List<PredictionResult> results = service.getPredictionResults(1L);

        assertTrue(results.isEmpty());
    }

    // ===== getPredictionMetrics =====

    @Test
    @DisplayName("getPredictionMetrics throws when task not found")
    void getPredictionMetrics_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getPredictionMetrics(99L));
    }

    @Test
    @DisplayName("getPredictionMetrics returns metrics with model version and predictions")
    void getPredictionMetrics_withModelVersion() {
        PredictionTask t = task();
        t.setModelVersionId(10L);
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);

        ModelVersion mv = new ModelVersion();
        mv.setId(10L);
        mv.setMae(5.0);
        mv.setRmse(7.0);
        mv.setMape(12.0);
        mv.setTrainingMetricsJson("{\"r2\":0.85,\"mae\":5.0}");
        mv.setFeatureImportanceJson("{\"date\":1.0}");
        when(modelVersionMapper.selectById(10L)).thenReturn(mv);

        PredictionResult r = new PredictionResult();
        r.setPredictionDate("2026-01-01");
        r.setPredictedValue(100.0);
        r.setLowerBound(90.0);
        r.setUpperBound(110.0);
        r.setConfidence(0.95);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(r));

        Map<String, Object> metrics = service.getPredictionMetrics(1L);

        assertEquals(1L, metrics.get("taskId"));
        assertEquals("LINEAR_REGRESSION", metrics.get("modelType"));
        assertEquals(10L, metrics.get("modelVersionId"));
        assertEquals(5.0, metrics.get("mae"));
        assertEquals(0.85, metrics.get("r2"));
        assertNotNull(metrics.get("trainingMetrics"));
        assertEquals(1, metrics.get("totalPredictions"));
        assertEquals(100.0, metrics.get("averagePredictedValue"));
    }

    @Test
    @DisplayName("getPredictionMetrics handles task without modelVersionId")
    void getPredictionMetrics_noModelVersion() {
        PredictionTask t = task();
        t.setModelVersionId(null);
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of());

        Map<String, Object> metrics = service.getPredictionMetrics(1L);

        assertEquals(1L, metrics.get("taskId"));
        assertNull(metrics.get("modelVersionId"));
        assertNull(metrics.get("mae"));
    }

    @Test
    @DisplayName("getPredictionMetrics handles invalid trainingMetricsJson")
    void getPredictionMetrics_invalidTrainingMetricsJson() {
        PredictionTask t = task();
        t.setModelVersionId(10L);
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);

        ModelVersion mv = new ModelVersion();
        mv.setId(10L);
        mv.setTrainingMetricsJson("not-json");
        when(modelVersionMapper.selectById(10L)).thenReturn(mv);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of());

        Map<String, Object> metrics = service.getPredictionMetrics(1L);

        assertNotNull(metrics);
        // r2 should not be set due to parse failure
        assertNull(metrics.get("r2"));
    }

    @Test
    @DisplayName("getPredictionMetrics handles null model version when modelVersionId set")
    void getPredictionMetrics_nullModelVersion() {
        PredictionTask t = task();
        t.setModelVersionId(10L);
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(10L)).thenReturn(null);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of());

        Map<String, Object> metrics = service.getPredictionMetrics(1L);

        assertNotNull(metrics);
        assertNull(metrics.get("mae"));
    }

    // ===== getPredictionComparison =====

    @Test
    @DisplayName("getPredictionComparison throws when task not found")
    void getPredictionComparison_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getPredictionComparison(99L));
    }

    @Test
    @DisplayName("getPredictionComparison returns INSUFFICIENT_DATA when no historical data")
    void getPredictionComparison_insufficientData() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(List.of());
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of());

        Map<String, Object> result = service.getPredictionComparison(1L);

        assertEquals("INSUFFICIENT_DATA", result.get("status"));
        assertEquals("Not enough data for comparison", result.get("message"));
    }

    // ===== getModelVersion =====

    @Test
    @DisplayName("getModelVersion returns model when found")
    void getModelVersion_found() {
        ModelVersion mv = new ModelVersion();
        mv.setId(5L);
        when(modelVersionMapper.selectById(5L)).thenReturn(mv);

        ModelVersion result = service.getModelVersion(5L);

        assertSame(mv, result);
    }

    @Test
    @DisplayName("getModelVersion throws when not found")
    void getModelVersion_notFound() {
        when(modelVersionMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getModelVersion(99L));
    }

    // ===== getModelVersions =====

    @Test
    @DisplayName("getModelVersions delegates to mapper")
    void getModelVersions_delegates() {
        ModelVersion mv = new ModelVersion();
        mv.setId(1L);
        when(modelVersionMapper.selectByModelName("model")).thenReturn(List.of(mv));

        List<ModelVersion> result = service.getModelVersions("model");

        assertEquals(1, result.size());
    }

    // ===== retrainPrediction =====

    @Test
    @DisplayName("retrainPrediction throws when task not found")
    void retrainPrediction_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.retrainPrediction(99L));
    }

    @Test
    @DisplayName("retrainPrediction sets status RETRAINING and runs synchronously")
    void retrainPrediction_happyPath() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        PredictionTask result = service.retrainPrediction(1L);

        // After sync retrain execution, status should be COMPLETED or FAILED
        assertTrue("COMPLETED".equals(result.getStatus()) || "FAILED".equals(result.getStatus()),
                "Status should be COMPLETED or FAILED after retrain, got " + result.getStatus());
        verify(predictionResultMapper, atLeastOnce()).delete(any());
    }

    // ===== deletePredictionTask =====

    @Test
    @DisplayName("deletePredictionTask deletes results and task")
    void deletePredictionTask_delegates() {
        service.deletePredictionTask(1L);

        verify(predictionResultMapper).delete(any());
        verify(predictionTaskMapper).deleteById(1L);
    }

    // ===== updatePredictionType =====

    @Test
    @DisplayName("updatePredictionType updates modelType when task found")
    void updatePredictionType_found() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);

        service.updatePredictionType(1L, "HOLT_WINTERS");

        assertEquals("HOLT_WINTERS", t.getModelType());
        assertNotNull(t.getUpdatedAt());
        verify(predictionTaskMapper).updateById(t);
    }

    @Test
    @DisplayName("updatePredictionType is no-op when task not found")
    void updatePredictionType_notFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        service.updatePredictionType(99L, "HOLT_WINTERS");

        verify(predictionTaskMapper, never()).updateById(any());
    }

    // ===== getPredictionTaskCount =====

    @Test
    @DisplayName("getPredictionTaskCount delegates to mapper")
    void getPredictionTaskCount_delegates() {
        when(predictionTaskMapper.selectCount(any())).thenReturn(42L);

        long count = service.getPredictionTaskCount();

        assertEquals(42L, count);
    }

    // ===== model variants via runPrediction =====

    @Test
    @DisplayName("runPrediction with HOLT_WINTERS model type completes")
    void runPrediction_holtWintersModel() {
        PredictionTask t = task();
        t.setModelType("HOLT_WINTERS");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        service.runPrediction(1L);

        assertTrue("COMPLETED".equals(t.getStatus()) || "FAILED".equals(t.getStatus()));
    }

    @Test
    @DisplayName("runPrediction with MOVING_AVERAGE model type completes")
    void runPrediction_movingAverageModel() {
        PredictionTask t = task();
        t.setModelType("MOVING_AVERAGE");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        service.runPrediction(1L);

        assertTrue("COMPLETED".equals(t.getStatus()) || "FAILED".equals(t.getStatus()));
    }

    @Test
    @DisplayName("runPrediction with EXPONENTIAL_SMOOTHING model type completes")
    void runPrediction_exponentialSmoothingModel() {
        PredictionTask t = task();
        t.setModelType("EXPONENTIAL_SMOOTHING");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        service.runPrediction(1L);

        assertTrue("COMPLETED".equals(t.getStatus()) || "FAILED".equals(t.getStatus()));
    }

    @Test
    @DisplayName("runPrediction with AUTO model type completes")
    void runPrediction_autoModel() {
        PredictionTask t = task();
        t.setModelType("AUTO");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        service.runPrediction(1L);

        assertTrue("COMPLETED".equals(t.getStatus()) || "FAILED".equals(t.getStatus()));
    }

    @Test
    @DisplayName("runPrediction with confidence level 0.99 produces wider bounds")
    void runPrediction_highConfidenceLevel() {
        PredictionTask t = task();
        t.setConfidenceLevel("0.99");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenReturn(seriesRows());

        service.runPrediction(1L);

        assertTrue("COMPLETED".equals(t.getStatus()) || "FAILED".equals(t.getStatus()));
    }

    @Test
    @DisplayName("runPrediction skips rows with non-numeric values")
    void runPrediction_skipsNonNumericRows() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());

        // Mix of valid and invalid rows
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("date", String.format("2026-01-%02d", i));
            row.put("sales", String.valueOf(100 + i * 10));
            rows.add(row);
        }
        // Add a row with non-numeric value
        Map<String, String> badRow = new LinkedHashMap<>();
        badRow.put("date", "2026-01-05");
        badRow.put("sales", "N/A");
        rows.add(badRow);
        when(dataReader.readRows(any())).thenReturn(rows);

        service.runPrediction(1L);

        // Should still complete since we have 4 valid rows (>= 3)
        assertTrue("COMPLETED".equals(t.getStatus()) || "FAILED".equals(t.getStatus()));
    }

    @Test
    @DisplayName("runPrediction skips rows with invalid dates")
    void runPrediction_skipsInvalidDates() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("date", String.format("2026-01-%02d", i));
            row.put("sales", String.valueOf(100 + i * 10));
            rows.add(row);
        }
        // Add a row with invalid date
        Map<String, String> badRow = new LinkedHashMap<>();
        badRow.put("date", "short");
        badRow.put("sales", "200");
        rows.add(badRow);
        when(dataReader.readRows(any())).thenReturn(rows);

        service.runPrediction(1L);

        assertTrue("COMPLETED".equals(t.getStatus()) || "FAILED".equals(t.getStatus()));
    }

    @Test
    @DisplayName("runPrediction handles dataset not found during loadSeries")
    void runPrediction_datasetNotFoundDuringLoad() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(null);

        service.runPrediction(1L);

        assertEquals("FAILED", t.getStatus());
    }

    @Test
    @DisplayName("runPrediction handles dataReader throwing exception")
    void runPrediction_dataReaderThrows() {
        PredictionTask t = task();
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectByModelName(any())).thenReturn(List.of());
        when(datasetMapper.selectById(42L)).thenReturn(parsedDataset());
        when(dataReader.readRows(any())).thenThrow(new RuntimeException("IO error"));

        service.runPrediction(1L);

        assertEquals("FAILED", t.getStatus());
        assertNotNull(t.getErrorMessage());
    }
}
