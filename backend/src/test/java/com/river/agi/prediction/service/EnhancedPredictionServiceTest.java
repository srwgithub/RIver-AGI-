package com.river.agi.prediction.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.river.agi.common.BusinessException;
import com.river.agi.common.ResourceAccessValidator;
import com.river.agi.common.SecurityUtils;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionEvaluation;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionEvaluationMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnhancedPredictionServiceTest {

    @Mock
    private PredictionTaskMapper predictionTaskMapper;
    @Mock
    private ModelVersionMapper modelVersionMapper;
    @Mock
    private PredictionResultMapper predictionResultMapper;
    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private DatasetDataReaderService dataReader;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private ResourceAccessValidator accessValidator;
    @Mock
    private PredictionEvaluationMapper evaluationMapper;
    @Mock
    private RuntimeMonitoringService runtimeMonitoringService;
    @Mock
    private DeepLearningPredictionService deepLearningPredictionService;
    @Mock
    private Executor taskExecutor;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private PredictionTask task(Long id, Long datasetId, String timeField, String targetField) {
        PredictionTask t = new PredictionTask();
        t.setId(id);
        t.setDatasetId(datasetId);
        t.setTimeField(timeField);
        t.setTargetField(targetField);
        t.setStatus("PENDING");
        t.setForecastDays(7);
        t.setTenantId(1L);
        t.setCreatedBy(1L);
        return t;
    }

    private Dataset dataset(Long id) {
        Dataset ds = new Dataset();
        ds.setId(id);
        ds.setName("ds-" + id);
        ds.setFileType("csv");
        ds.setCreatedBy(1L);
        ds.setTenantId(1L);
        return ds;
    }

    private Authentication auth() {
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
        return authentication;
    }

    private List<Map<String, String>> rows(String dateField, String targetField, String... pairs) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put(dateField, pairs[i]);
            row.put(targetField, pairs[i + 1]);
            rows.add(row);
        }
        return rows;
    }

    private PredictionAlgorithm fakeAlgorithm(String type, String name) {
        return fakeAlgorithm(type, name, new ModelVersion(), new ArrayList<>());
    }

    private PredictionAlgorithm fakeAlgorithm(String type, String name, ModelVersion trained, List<PredictionResult> predicted) {
        return new PredictionAlgorithm() {
            @Override public String getAlgorithmName() { return name; }
            @Override public String getAlgorithmType() { return type; }
            @Override public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) { return trained; }
            @Override public List<PredictionResult> predict(PredictionTask task, List<PredictionData.SeriesPoint> series,
                                                              ModelVersion modelVersion, int forecastDays) { return predicted; }
            @Override public Map<String, Object> getAlgorithmParams(PredictionTask task) { return Map.of(); }
        };
    }

    private EnhancedPredictionService createService(List<PredictionAlgorithm> algorithms) {
        EnhancedPredictionService svc = new EnhancedPredictionService(
                predictionTaskMapper, modelVersionMapper, predictionResultMapper,
                datasetMapper, objectMapper, securityUtils, accessValidator, dataReader, algorithms, evaluationMapper);
        svc.setTaskExecutor(taskExecutor);
        svc.setRuntimeMonitoringService(runtimeMonitoringService);
        svc.setDeepLearningPredictionService(deepLearningPredictionService);
        return svc;
    }

    private EnhancedPredictionService createServiceNoExecutor(List<PredictionAlgorithm> algorithms) {
        EnhancedPredictionService svc = new EnhancedPredictionService(
                predictionTaskMapper, modelVersionMapper, predictionResultMapper,
                datasetMapper, objectMapper, securityUtils, accessValidator, dataReader, algorithms, evaluationMapper);
        // Don't set taskExecutor - test sync execution path
        svc.setRuntimeMonitoringService(runtimeMonitoringService);
        svc.setDeepLearningPredictionService(deepLearningPredictionService);
        return svc;
    }

    private EnhancedPredictionService createServiceNoEvalMapper(List<PredictionAlgorithm> algorithms) {
        EnhancedPredictionService svc = new EnhancedPredictionService(
                predictionTaskMapper, modelVersionMapper, predictionResultMapper,
                datasetMapper, objectMapper, securityUtils, accessValidator, dataReader, algorithms);
        return svc;
    }

    // ============ getPredictionTaskCount ============

    @Test
    @DisplayName("Should return prediction task count")
    void getPredictionTaskCount() {
        when(predictionTaskMapper.selectCount(any())).thenReturn(10L);

        EnhancedPredictionService service = createService(new ArrayList<>());
        long count = service.getPredictionTaskCount();
        assertEquals(10L, count);
    }

    // ============ compareModelVersions ============

    @Test
    @DisplayName("Should compare model versions successfully")
    void compareModelVersions() {
        ModelVersion model1 = new ModelVersion();
        model1.setId(1L);
        model1.setModelName("test");
        model1.setModelType("LINEAR_REGRESSION");
        model1.setVersionNumber(1);
        model1.setMae(5.0);
        model1.setRmse(10.0);
        model1.setMape(5.0);

        ModelVersion model2 = new ModelVersion();
        model2.setId(2L);
        model2.setModelName("test");
        model2.setModelType("EXPONENTIAL_SMOOTHING");
        model2.setVersionNumber(2);
        model2.setMae(3.0);
        model2.setRmse(7.0);
        model2.setMape(3.0);

        when(modelVersionMapper.selectById(1L)).thenReturn(model1);
        when(modelVersionMapper.selectById(2L)).thenReturn(model2);

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> comparison = service.compareModelVersions(1L, 2L);
        assertNotNull(comparison);
        assertEquals("model2", comparison.get("recommendation"));
    }

    @Test
    @DisplayName("Should throw exception when model not found in comparison")
    void compareModelVersions_notFound() {
        when(modelVersionMapper.selectById(anyLong())).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.compareModelVersions(1L, 2L));
    }

    @Test
    @DisplayName("Should handle model version with null metrics in comparison - falls back to MAE")
    void compareModelVersions_nullMetrics_fallsBackToMae() {
        ModelVersion model1 = new ModelVersion();
        model1.setId(1L);
        model1.setModelName("test");
        model1.setVersionNumber(1);
        model1.setMae(3.0);
        // rmse is null

        ModelVersion model2 = new ModelVersion();
        model2.setId(2L);
        model2.setModelName("test");
        model2.setVersionNumber(2);
        model2.setMae(5.0);
        // rmse is null

        when(modelVersionMapper.selectById(1L)).thenReturn(model1);
        when(modelVersionMapper.selectById(2L)).thenReturn(model2);

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> comparison = service.compareModelVersions(1L, 2L);
        // model1 has lower mae -> recommended
        assertEquals("model1", comparison.get("recommendation"));
    }

    @Test
    @DisplayName("Should handle both null metrics in comparison - defaults to model1")
    void compareModelVersions_allMetricsNull_defaultsToModel1() {
        ModelVersion model1 = new ModelVersion();
        model1.setId(1L);
        model1.setModelName("test");
        model1.setVersionNumber(1);

        ModelVersion model2 = new ModelVersion();
        model2.setId(2L);
        model2.setModelName("test");
        model2.setVersionNumber(2);

        when(modelVersionMapper.selectById(1L)).thenReturn(model1);
        when(modelVersionMapper.selectById(2L)).thenReturn(model2);

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> comparison = service.compareModelVersions(1L, 2L);
        assertEquals("model1", comparison.get("recommendation"));
    }

    // ============ rollbackToModelVersion ============

    @Test
    @DisplayName("Should rollback model version")
    void rollbackToModelVersion() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setStatus("COMPLETED");

        ModelVersion version = new ModelVersion();
        version.setId(5L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(modelVersionMapper.selectById(5L)).thenReturn(version);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertDoesNotThrow(() -> service.rollbackToModelVersion(1L, 5L));
        verify(predictionTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("Should throw when rollback task not found")
    void rollbackToModelVersion_taskNotFound() {
        when(predictionTaskMapper.selectById(1L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rollbackToModelVersion(1L, 5L));
        assertEquals("Prediction task not found", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw when rollback target not found")
    void rollbackToModelVersion_targetNotFound() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(modelVersionMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.rollbackToModelVersion(1L, 999L));
    }

    // ============ getPredictionTasks / getPredictionResults ============

    @Test
    @DisplayName("Should get prediction tasks")
    void getPredictionTasks() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setStatus("COMPLETED");

        Page<PredictionTask> page = new Page<>(1, 10);
        page.setRecords(List.of(task));
        page.setTotal(1L);
        when(predictionTaskMapper.selectPage(any(), any())).thenReturn(page);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertNotNull(service.getPredictionTasks(1, 10));
    }

    @Test
    @DisplayName("Should return prediction results by task ID")
    void getPredictionResults() {
        PredictionResult result = new PredictionResult();
        result.setTaskId(1L);
        result.setPredictedValue(100.0);

        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(result));

        EnhancedPredictionService service = createService(new ArrayList<>());
        List<PredictionResult> results = service.getPredictionResults(1L);
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Should handle empty prediction results")
    void getPredictionResults_empty() {
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        List<PredictionResult> results = service.getPredictionResults(1L);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // ============ getModelVersions / getModelVersion ============

    @Test
    @DisplayName("Should get model versions by name")
    void getModelVersions() {
        ModelVersion version = new ModelVersion();
        version.setId(1L);
        version.setModelName("test_model");

        when(modelVersionMapper.selectByModelName("test_model")).thenReturn(List.of(version));

        EnhancedPredictionService service = createService(new ArrayList<>());
        List<ModelVersion> versions = service.getModelVersions("test_model");
        assertNotNull(versions);
        assertEquals(1, versions.size());
    }

    @Test
    @DisplayName("Should get model version by ID")
    void getModelVersion() {
        ModelVersion version = new ModelVersion();
        version.setId(1L);

        when(modelVersionMapper.selectById(1L)).thenReturn(version);

        EnhancedPredictionService service = createService(new ArrayList<>());
        ModelVersion result = service.getModelVersion(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw when model version not found")
    void getModelVersion_notFound() {
        when(modelVersionMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.getModelVersion(999L));
    }

    // ============ getPredictionTask ============

    @Test
    @DisplayName("Should get prediction task by ID")
    void getPredictionTask() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);

        EnhancedPredictionService service = createService(new ArrayList<>());
        PredictionTask result = service.getPredictionTask(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw when prediction task not found")
    void getPredictionTask_notFound() {
        when(predictionTaskMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.getPredictionTask(999L));
    }

    // ============ getPredictionMetrics ============

    @Test
    @DisplayName("Should get prediction metrics with model version and training metrics")
    void getPredictionMetrics_withModel() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setStatus("COMPLETED");
        task.setTargetField("sales");
        task.setTimeField("date");
        task.setModelType("LINEAR_REGRESSION");
        task.setModelVersionId(10L);

        ModelVersion version = new ModelVersion();
        version.setId(10L);
        version.setMae(3.5);
        version.setRmse(7.2);
        version.setMape(4.1);
        version.setTrainingMetricsJson("{\"r2\":0.85,\"algorithm\":\"LINEAR_REGRESSION\"}");

        PredictionResult result = new PredictionResult();
        result.setPredictionDate("2026-01-01");
        result.setPredictedValue(100.0);
        result.setLowerBound(90.0);
        result.setUpperBound(110.0);
        result.setConfidence(0.9);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(modelVersionMapper.selectById(10L)).thenReturn(version);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(result));

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> metrics = service.getPredictionMetrics(1L);
        assertNotNull(metrics);
        assertEquals(3.5, metrics.get("mae"));
        assertEquals(7.2, metrics.get("rmse"));
        assertEquals(4.1, metrics.get("mape"));
        assertEquals(0.85, metrics.get("r2"));
        assertEquals("LINEAR_REGRESSION", metrics.get("algorithm"));
        assertEquals(1, metrics.get("totalPredictions"));
        assertEquals(100.0, metrics.get("averagePredictedValue"));
    }

    @Test
    @DisplayName("Should throw when getting metrics for non-existent task")
    void getPredictionMetrics_taskNotFound() {
        when(predictionTaskMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.getPredictionMetrics(999L));
    }

    @Test
    @DisplayName("Should get metrics without model version id")
    void getPredictionMetrics_noModelVersion() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setStatus("PENDING");

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> metrics = service.getPredictionMetrics(1L);
        assertNotNull(metrics);
        assertEquals("PENDING", metrics.get("status"));
        assertNull(metrics.get("mae"));
    }

    @Test
    @DisplayName("Should get metrics with invalid trainingMetricsJson - logs warning")
    void getPredictionMetrics_invalidTrainingMetricsJson() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setStatus("COMPLETED");
        task.setModelVersionId(10L);

        ModelVersion version = new ModelVersion();
        version.setId(10L);
        version.setMae(3.5);
        version.setTrainingMetricsJson("invalid-json");

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(modelVersionMapper.selectById(10L)).thenReturn(version);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> metrics = service.getPredictionMetrics(1L);
        assertNotNull(metrics);
        assertEquals(3.5, metrics.get("mae"));
        // r2/algorithm should not be set since JSON parse failed
        assertNull(metrics.get("r2"));
    }

    @Test
    @DisplayName("Should get metrics with model version not found")
    void getPredictionMetrics_modelVersionNotFound() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setModelVersionId(10L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(modelVersionMapper.selectById(10L)).thenReturn(null);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> metrics = service.getPredictionMetrics(1L);
        assertNotNull(metrics);
        assertNull(metrics.get("mae"));
    }

    // ============ createPredictionTask ============

    @Test
    @DisplayName("createPredictionTask - dataset not found throws")
    void createPredictionTask_datasetNotFound() {
        PredictionTask t = task(null, 1L, "date", "sales");
        when(datasetMapper.selectById(1L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        assertEquals("Dataset not found", ex.getMessage());
    }

    @Test
    @DisplayName("createPredictionTask - blank timeField throws")
    void createPredictionTask_blankTimeField() {
        PredictionTask t = task(null, 1L, " ", "sales");
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        assertTrue(ex.getMessage().contains("时间字段"));
    }

    @Test
    @DisplayName("createPredictionTask - blank targetField throws")
    void createPredictionTask_blankTargetField() {
        PredictionTask t = task(null, 1L, "date", "");
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        assertTrue(ex.getMessage().contains("目标字段"));
    }

    @Test
    @DisplayName("createPredictionTask - forecastDays out of range throws")
    void createPredictionTask_invalidForecastDays() {
        PredictionTask t = task(null, 1L, "date", "sales");
        t.setForecastDays(0);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        assertTrue(ex.getMessage().contains("预测天数"));
    }

    @Test
    @DisplayName("createPredictionTask - forecastDays exceeds max throws")
    void createPredictionTask_forecastDaysExceedsMax() {
        PredictionTask t = task(null, 1L, "date", "sales");
        t.setForecastDays(400);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.createPredictionTask(t, auth()));
    }

    @Test
    @DisplayName("createPredictionTask - loadSeries throws wraps as data validation failure")
    void createPredictionTask_loadSeriesThrows() {
        PredictionTask t = task(null, 1L, "date", "sales");
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        when(dataReader.readRows(any())).thenThrow(new RuntimeException("io fail"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        assertTrue(ex.getMessage().contains("数据校验失败"));
    }

    @Test
    @DisplayName("createPredictionTask - insufficient data points throws")
    void createPredictionTask_insufficientDataPoints() {
        PredictionTask t = task(null, 1L, "date", "sales");
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        assertTrue(ex.getMessage().contains("有效数据点不足"));
    }

    @Test
    @DisplayName("createPredictionTask - all rows invalid (bad dates) results in insufficient points")
    void createPredictionTask_allDatesInvalid_fallsBackToInsufficientPoints() {
        PredictionTask t = task(null, 1L, "date", "sales");
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "bad-date", "10", "bad-date-2", "20", "bad-date-3", "30"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        // With 0 valid points, hits "有效数据点不足" branch first
        assertTrue(ex.getMessage().contains("有效数据点不足"));
    }

    @Test
    @DisplayName("createPredictionTask - all non-numeric values results in insufficient points")
    void createPredictionTask_allValuesNonNumeric_fallsBackToInsufficientPoints() {
        PredictionTask t = task(null, 1L, "date", "sales");
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "abc", "2026-01-02", "def", "2026-01-03", "ghi"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createPredictionTask(t, auth()));
        // With 0 valid points, hits "有效数据点不足" branch first
        assertTrue(ex.getMessage().contains("有效数据点不足"));
    }

    @Test
    @DisplayName("createPredictionTask - success path inserts task")
    void createPredictionTask_success() {
        PredictionTask t = task(null, 1L, "date", "sales");
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(securityUtils.getCurrentUserId(any())).thenReturn(1L);
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        PredictionTask result = service.createPredictionTask(t, auth());
        assertEquals("PENDING", result.getStatus());
        assertEquals(1L, result.getCreatedBy());
        verify(predictionTaskMapper).insert(t);
        verify(accessValidator).validateDatasetOwnership(eq(1L), eq(1L));
    }

    // ============ runPrediction ============

    @Test
    @DisplayName("runPrediction - task not found throws")
    void runPrediction_taskNotFound() {
        when(predictionTaskMapper.selectById(1L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.runPrediction(1L));
    }

    @Test
    @DisplayName("runPrediction - uses taskExecutor when configured")
    void runPrediction_withExecutor() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);

        EnhancedPredictionService service = createService(new ArrayList<>());
        PredictionTask result = service.runPrediction(1L);
        assertEquals("RUNNING", result.getStatus());
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("runPrediction - synchronous execution when no taskExecutor")
    void runPrediction_syncExecution() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);

        // Use service without executor - prediction will fail because dataset lookup fails
        EnhancedPredictionService service = createServiceNoExecutor(new ArrayList<>());
        PredictionTask result = service.runPrediction(1L);
        assertEquals("FAILED", result.getStatus());
        verify(predictionTaskMapper, atLeast(2)).selectById(1L);
    }

    // ============ getEvaluationHistory ============

    @Test
    @DisplayName("getEvaluationHistory - returns list from mapper")
    void getEvaluationHistory_withMapper() {
        PredictionEvaluation e = new PredictionEvaluation();
        e.setId(1L);
        when(evaluationMapper.selectByTaskId(5L)).thenReturn(List.of(e));

        EnhancedPredictionService service = createService(new ArrayList<>());
        List<PredictionEvaluation> result = service.getEvaluationHistory(5L);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getEvaluationHistory - null mapper returns empty list")
    void getEvaluationHistory_nullMapper() {
        EnhancedPredictionService service = createServiceNoEvalMapper(new ArrayList<>());
        List<PredictionEvaluation> result = service.getEvaluationHistory(5L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ============ evaluateAndRecord ============

    @Test
    @DisplayName("evaluateAndRecord - null mapper throws")
    void evaluateAndRecord_nullMapper() {
        EnhancedPredictionService service = createServiceNoEvalMapper(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.evaluateAndRecord(1L));
        assertEquals("评估存储未配置", ex.getMessage());
    }

    @Test
    @DisplayName("evaluateAndRecord - high mape returns NEEDS_OPTIMIZATION status")
    void evaluateAndRecord_needsOptimization() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        t.setModelVersionId(10L);

        ModelVersion mv = new ModelVersion();
        mv.setId(10L);
        mv.setMae(5.0);
        mv.setRmse(7.0);
        mv.setMape(50.0);  // accuracy = 1 - 0.5 = 0.5 < 0.8

        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(10L)).thenReturn(mv);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        PredictionEvaluation result = service.evaluateAndRecord(1L);

        assertEquals("NEEDS_OPTIMIZATION", result.getStatus());
        assertEquals("建议自动调优并重新训练", result.getRecommendation());
        verify(evaluationMapper).insert(any(PredictionEvaluation.class));
    }

    @Test
    @DisplayName("evaluateAndRecord - low mape returns PASSED status")
    void evaluateAndRecord_passed() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        t.setModelVersionId(10L);
        t.setModelType("LINEAR_REGRESSION");

        ModelVersion mv = new ModelVersion();
        mv.setId(10L);
        mv.setMae(1.0);
        mv.setRmse(2.0);
        mv.setMape(5.0);  // accuracy = 1 - 0.05 = 0.95 >= 0.8

        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(modelVersionMapper.selectById(10L)).thenReturn(mv);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        PredictionEvaluation result = service.evaluateAndRecord(1L);

        assertEquals("PASSED", result.getStatus());
        assertEquals("当前模型效果稳定", result.getRecommendation());
    }

    // ============ deletePredictionTask ============

    @Test
    @DisplayName("deletePredictionTask - deletes results and task")
    void deletePredictionTask() {
        EnhancedPredictionService service = createService(new ArrayList<>());
        service.deletePredictionTask(7L);

        verify(predictionResultMapper).delete(any());
        verify(predictionTaskMapper).deleteById(7L);
    }

    // ============ getAvailableAlgorithms ============

    @Test
    @DisplayName("Should retrieve available algorithms")
    void getAvailableAlgorithms() {
        EnhancedPredictionService service = createService(List.of(
                fakeAlgorithm("LINEAR_REGRESSION", "线性回归"),
                fakeAlgorithm("MOVING_AVERAGE", "移动平均")
        ));
        List<Map<String, Object>> algorithms = service.getAvailableAlgorithms();
        assertNotNull(algorithms);
        assertEquals(2, algorithms.size());
        assertEquals("LINEAR_REGRESSION", algorithms.get(0).get("type"));
    }

    // ============ detectPredictionBias ============

    @Test
    @DisplayName("detectPredictionBias - task not found throws")
    void detectPredictionBias_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.detectPredictionBias(99L));
    }

    @Test
    @DisplayName("detectPredictionBias - empty data returns insufficient data")
    void detectPredictionBias_emptyData() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        when(dataReader.readRows(any())).thenReturn(new ArrayList<>());
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.detectPredictionBias(1L);
        assertEquals(false, result.get("biasDetected"));
        assertEquals("Insufficient data for bias detection", result.get("message"));
    }

    @Test
    @DisplayName("detectPredictionBias - predictions without matching actuals returns evaluationAvailable=false")
    void detectPredictionBias_noMatchingActuals() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        // actual data has dates 2026-01-01, 2026-01-02
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20"));
        // predictions for future dates that have no actuals
        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-12-01");
        pr.setPredictedValue(100.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr));

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.detectPredictionBias(1L);
        assertEquals(false, result.get("evaluationAvailable"));
        assertEquals(false, result.get("biasDetected"));
    }

    @Test
    @DisplayName("detectPredictionBias - predictions with matching actuals computes bias")
    void detectPredictionBias_withMatchingActuals() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        // actual data for 2026-01-01 to 2026-01-03 with value 100 each
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "100", "2026-01-02", "100", "2026-01-03", "100"));

        // predictions for same dates - all 160 (over-prediction, avgError=60, biasPct=60/200*100=30 > 25)
        // biasPercentage = 30 > 10 threshold => biasDetected=true
        // severity HIGH because |30| > 25
        PredictionResult pr1 = new PredictionResult();
        pr1.setPredictionDate("2026-01-01");
        pr1.setPredictedValue(160.0);
        PredictionResult pr2 = new PredictionResult();
        pr2.setPredictionDate("2026-01-02");
        pr2.setPredictedValue(160.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr1, pr2));

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.detectPredictionBias(1L);
        assertEquals(true, result.get("evaluationAvailable"));
        assertEquals(2, result.get("overPredictions"));
        assertEquals(true, result.get("biasDetected"));
        assertEquals("OVER_PREDICTION", result.get("biasDirection"));
        assertEquals("HIGH", result.get("severity"));
    }

    @Test
    @DisplayName("detectPredictionBias - under-prediction sets UNDER_PREDICTION direction")
    void detectPredictionBias_underPrediction() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "100", "2026-01-02", "100"));

        // predictions much lower than actuals (under-prediction by 50%)
        // biasPercentage = (-50/100) * 100 = -50, |biasPercentage| = 50 > 10
        PredictionResult pr1 = new PredictionResult();
        pr1.setPredictionDate("2026-01-01");
        pr1.setPredictedValue(50.0);
        PredictionResult pr2 = new PredictionResult();
        pr2.setPredictionDate("2026-01-02");
        pr2.setPredictedValue(50.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr1, pr2));

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.detectPredictionBias(1L);
        assertEquals(true, result.get("biasDetected"));
        assertEquals("UNDER_PREDICTION", result.get("biasDirection"));
        assertEquals(2, result.get("underPredictions"));
    }

    @Test
    @DisplayName("detectPredictionBias - prediction uses actualValue when present")
    void detectPredictionBias_usesActualValueFromResult() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        // actual data has dates that won't match prediction dates
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2025-01-01", "10"));

        // prediction has actualValue set explicitly
        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-01-01");
        pr.setPredictedValue(120.0);
        pr.setActualValue(100.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr));

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.detectPredictionBias(1L);
        assertEquals(true, result.get("evaluationAvailable"));
        // actual = 100, predicted = 120 -> over-prediction
        assertEquals(1, result.get("overPredictions"));
    }

    @Test
    @DisplayName("detectPredictionBias - low bias within threshold returns LOW severity")
    void detectPredictionBias_lowBias() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "100", "2026-01-02", "100"));

        // predictions very close to actual (within 5% bias threshold)
        PredictionResult pr1 = new PredictionResult();
        pr1.setPredictionDate("2026-01-01");
        pr1.setPredictedValue(101.0);
        PredictionResult pr2 = new PredictionResult();
        pr2.setPredictionDate("2026-01-02");
        pr2.setPredictedValue(101.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr1, pr2));

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.detectPredictionBias(1L);
        // avgError = 1, biasPercentage = 1% < 10% threshold
        assertEquals(false, result.get("biasDetected"));
        assertEquals("LOW", result.get("severity"));
    }

    // ============ autoRetrainOnBias ============

    @Test
    @DisplayName("autoRetrainOnBias - no bias detected, no retrain triggered")
    void autoRetrainOnBias_noBias() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        when(dataReader.readRows(any())).thenReturn(new ArrayList<>());
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.autoRetrainOnBias(1L);
        assertEquals(false, result.get("retrainTriggered"));
        assertEquals(false, result.get("cooldownActive"));
    }

    @Test
    @DisplayName("autoRetrainOnBias - bias detected, no cooldown, triggers retrain")
    void autoRetrainOnBias_biasTriggersRetrain() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        // 3+ data points required for retrain
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "100", "2026-01-02", "100", "2026-01-03", "100"));
        PredictionResult pr1 = new PredictionResult();
        pr1.setPredictionDate("2026-01-01");
        pr1.setPredictedValue(150.0);
        PredictionResult pr2 = new PredictionResult();
        pr2.setPredictionDate("2026-01-02");
        pr2.setPredictedValue(150.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr1, pr2));

        // No latest retraining -> no cooldown
        when(evaluationMapper.selectLatestRetraining(1L)).thenReturn(null);

        // Retrain path: dataset lookup + data load succeeds (3 points)
        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.autoRetrainOnBias(1L);
        assertEquals(true, result.get("retrainTriggered"));
        assertEquals(false, result.get("cooldownActive"));
        verify(evaluationMapper).insert(any(PredictionEvaluation.class));
    }

    @Test
    @DisplayName("autoRetrainOnBias - bias detected but cooldown active, skips retrain")
    void autoRetrainOnBias_cooldownActive() {
        PredictionTask t = task(1L, 100L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(100L)).thenReturn(dataset(100L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "100", "2026-01-02", "100", "2026-01-03", "100"));
        PredictionResult pr1 = new PredictionResult();
        pr1.setPredictionDate("2026-01-01");
        pr1.setPredictedValue(150.0);
        PredictionResult pr2 = new PredictionResult();
        pr2.setPredictionDate("2026-01-02");
        pr2.setPredictedValue(150.0);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(pr1, pr2));

        // Latest retraining was 5 minutes ago - within cooldown window
        PredictionEvaluation latest = new PredictionEvaluation();
        latest.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(evaluationMapper.selectLatestRetraining(1L)).thenReturn(latest);

        EnhancedPredictionService service = createService(new ArrayList<>());
        Map<String, Object> result = service.autoRetrainOnBias(1L);
        assertEquals(false, result.get("retrainTriggered"));
        assertEquals(true, result.get("cooldownActive"));
        assertEquals(60L, result.get("cooldownMinutes"));
    }

    // ============ retrainPrediction ============

    @Test
    @DisplayName("retrainPrediction - task not found throws")
    void retrainPrediction_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.retrainPrediction(99L));
    }

    @Test
    @DisplayName("retrainPrediction - already retraining throws")
    void retrainPrediction_alreadyRetraining() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        t.setStatus("RETRAINING");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrainPrediction(1L));
        assertTrue(ex.getMessage().contains("正在 Retraining"));
    }

    @Test
    @DisplayName("retrainPrediction - dataset not found throws")
    void retrainPrediction_datasetNotFound() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrainPrediction(1L));
        assertTrue(ex.getMessage().contains("关联数据集不存在"));
    }

    @Test
    @DisplayName("retrainPrediction - missing time/target field throws")
    void retrainPrediction_missingFields() {
        PredictionTask t = task(1L, 1L, " ", "");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrainPrediction(1L));
        assertTrue(ex.getMessage().contains("缺少时间字段或目标字段"));
    }

    @Test
    @DisplayName("retrainPrediction - loadSeries throws wraps as data read failure")
    void retrainPrediction_loadSeriesThrows() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenThrow(new RuntimeException("io fail"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrainPrediction(1L));
        assertTrue(ex.getMessage().contains("无法读取任务数据"));
    }

    @Test
    @DisplayName("retrainPrediction - insufficient data points throws")
    void retrainPrediction_insufficientPoints() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrainPrediction(1L));
        assertTrue(ex.getMessage().contains("有效数据点不足"));
    }

    @Test
    @DisplayName("retrainPrediction - success path sets RETRAINING status")
    void retrainPrediction_success() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        PredictionTask result = service.retrainPrediction(1L);
        assertEquals("RETRAINING", result.getStatus());
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("retrainPrediction - sync execution when no taskExecutor")
    void retrainPrediction_syncExecution() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30"));

        EnhancedPredictionService service = createServiceNoExecutor(new ArrayList<>());
        PredictionTask result = service.retrainPrediction(1L);
        // sync executeRetrainJob runs immediately; with no algorithms, task ends FAILED
        // but the returned task still has RETRAINING status (set before async call)
        // The sync path updates the task object in-place, so final status is FAILED
        assertNotNull(result);
    }

    // ============ autoTune ============

    @Test
    @DisplayName("autoTune - task not found throws")
    void autoTune_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.autoTune(99L));
    }

    @Test
    @DisplayName("autoTune - insufficient data points throws")
    void autoTune_insufficientPoints() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.autoTune(1L));
        assertTrue(ex.getMessage().contains("有效数据点不足"));
    }

    @Test
    @DisplayName("autoTune - all algorithms fail predict, throws no available algorithm")
    void autoTune_allPredictionsFail() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30",
                "2026-01-04", "40", "2026-01-05", "50"));

        // Algorithm whose predict throws - train returns model with all metrics set
        PredictionAlgorithm algo = new PredictionAlgorithm() {
            @Override public String getAlgorithmName() { return "failing"; }
            @Override public String getAlgorithmType() { return "FAILING"; }
            @Override public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
                ModelVersion mv = new ModelVersion();
                mv.setModelName("test");
                mv.setMae(1.0);
                mv.setRmse(1.0);
                mv.setMape(1.0);
                return mv;
            }
            @Override public List<PredictionResult> predict(PredictionTask task, List<PredictionData.SeriesPoint> series,
                                                              ModelVersion modelVersion, int forecastDays) {
                throw new RuntimeException("predict failed");
            }
            @Override public Map<String, Object> getAlgorithmParams(PredictionTask task) { return Map.of(); }
        };

        EnhancedPredictionService service = createService(List.of(algo));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.autoTune(1L));
        assertEquals("没有可用预测算法", ex.getMessage());
    }

    @Test
    @DisplayName("autoTune - happy path with single working algorithm")
    void autoTune_happyPath() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30", "2026-01-04", "40", "2026-01-05", "50"));

        ModelVersion trained = new ModelVersion();
        trained.setModelName("test_model");
        trained.setMae(1.0);
        trained.setRmse(2.0);
        trained.setMape(5.0);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-02-01");
        pr.setPredictedValue(60.0);

        PredictionAlgorithm algo = fakeAlgorithm("LINEAR_REGRESSION", "线性回归", trained, List.of(pr));
        when(modelVersionMapper.selectByModelName("test_model")).thenReturn(new ArrayList<>());
        when(modelVersionMapper.insert(any(ModelVersion.class))).thenAnswer(inv -> {
            ((ModelVersion) inv.getArgument(0)).setId(99L);
            return 1;
        });

        EnhancedPredictionService service = createService(List.of(algo));
        Map<String, Object> result = service.autoTune(1L);

        assertEquals("OPTIMIZED", result.get("status"));
        assertEquals("LINEAR_REGRESSION", result.get("selectedAlgorithm"));
        verify(modelVersionMapper).insert(any(ModelVersion.class));
        verify(predictionResultMapper, atLeast(1)).insert(any(PredictionResult.class));
        verify(evaluationMapper).insert(any(PredictionEvaluation.class));
    }

    @Test
    @DisplayName("autoTune - candidate train failure records FAILED status and continues")
    void autoTune_trainFailureContinues() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30",
                "2026-01-04", "40", "2026-01-05", "50"));

        // First algorithm fails train, second succeeds
        PredictionAlgorithm failingAlgo = new PredictionAlgorithm() {
            @Override public String getAlgorithmName() { return "failing"; }
            @Override public String getAlgorithmType() { return "FAILING"; }
            @Override public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
                throw new RuntimeException("train failed");
            }
            @Override public List<PredictionResult> predict(PredictionTask task, List<PredictionData.SeriesPoint> series,
                                                              ModelVersion modelVersion, int forecastDays) {
                return new ArrayList<>();
            }
            @Override public Map<String, Object> getAlgorithmParams(PredictionTask task) { return Map.of(); }
        };

        ModelVersion trained = new ModelVersion();
        trained.setModelName("ok_model");
        trained.setMae(1.0);
        trained.setRmse(2.0);
        trained.setMape(5.0);
        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-02-01");
        pr.setPredictedValue(60.0);
        PredictionAlgorithm okAlgo = fakeAlgorithm("LINEAR_REGRESSION", "线性回归", trained, List.of(pr));
        when(modelVersionMapper.selectByModelName("ok_model")).thenReturn(new ArrayList<>());
        when(modelVersionMapper.insert(any(ModelVersion.class))).thenAnswer(inv -> {
            ((ModelVersion) inv.getArgument(0)).setId(99L);
            return 1;
        });

        EnhancedPredictionService service = createService(List.of(failingAlgo, okAlgo));
        Map<String, Object> result = service.autoTune(1L);

        assertEquals("OPTIMIZED", result.get("status"));
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        // First candidate FAILED, second OK
        assertEquals("FAILED", candidates.get(0).get("status"));
    }

    // ============ manualTune ============

    @Test
    @DisplayName("manualTune - task not found throws")
    void manualTune_taskNotFound() {
        when(predictionTaskMapper.selectById(99L)).thenReturn(null);

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.manualTune(99L, Map.of()));
    }

    @Test
    @DisplayName("manualTune - insufficient data points throws")
    void manualTune_insufficientPoints() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10"));

        EnhancedPredictionService service = createService(new ArrayList<>());
        assertThrows(BusinessException.class, () -> service.manualTune(1L, Map.of()));
    }

    @Test
    @DisplayName("manualTune - success path with parameters")
    void manualTune_success() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30",
                "2026-01-04", "40", "2026-01-05", "50"));

        ModelVersion trained = new ModelVersion();
        trained.setModelName("manual_model");
        trained.setMae(1.0);
        trained.setRmse(2.0);
        trained.setMape(5.0);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-02-01");
        pr.setPredictedValue(60.0);

        PredictionAlgorithm algo = fakeAlgorithm("LINEAR_REGRESSION", "线性回归", trained, List.of(pr));
        when(modelVersionMapper.selectByModelName("manual_model")).thenReturn(new ArrayList<>());
        when(modelVersionMapper.insert(any(ModelVersion.class))).thenAnswer(inv -> {
            ((ModelVersion) inv.getArgument(0)).setId(88L);
            return 1;
        });

        EnhancedPredictionService service = createService(List.of(algo));
        Map<String, Object> result = service.manualTune(1L, Map.of("alpha", 0.5));

        assertEquals("OPTIMIZED", result.get("status"));
        assertEquals("LINEAR_REGRESSION", result.get("selectedAlgorithm"));
        Map<String, Object> applied = (Map<String, Object>) result.get("parameters");
        assertEquals(0.5, applied.get("alpha"));
        assertEquals("MANUAL", applied.get("tuningMode"));
        verify(evaluationMapper).insert(any(PredictionEvaluation.class));
    }

    @Test
    @DisplayName("manualTune - null requestedParameters uses defaults")
    void manualTune_nullParameters() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30",
                "2026-01-04", "40", "2026-01-05", "50"));

        ModelVersion trained = new ModelVersion();
        trained.setModelName("manual_model");
        trained.setMae(1.0);
        trained.setRmse(2.0);
        trained.setMape(5.0);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-02-01");
        pr.setPredictedValue(60.0);

        PredictionAlgorithm algo = fakeAlgorithm("LINEAR_REGRESSION", "线性回归", trained, List.of(pr));
        when(modelVersionMapper.selectByModelName("manual_model")).thenReturn(new ArrayList<>());
        when(modelVersionMapper.insert(any(ModelVersion.class))).thenAnswer(inv -> {
            ((ModelVersion) inv.getArgument(0)).setId(88L);
            return 1;
        });

        EnhancedPredictionService service = createService(List.of(algo));
        Map<String, Object> result = service.manualTune(1L, null);

        assertEquals("OPTIMIZED", result.get("status"));
    }

    // ============ executePredictionAsync (via runPrediction) ============

    @Test
    @DisplayName("runPrediction - async execution completes with COMPLETED status")
    void runPrediction_asyncCompletes() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10", "2026-01-02", "20", "2026-01-03", "30",
                "2026-01-04", "40", "2026-01-05", "50"));

        ModelVersion trained = new ModelVersion();
        trained.setModelName("predict_model");
        trained.setMae(1.0);
        trained.setRmse(2.0);
        trained.setMape(5.0);

        PredictionResult pr = new PredictionResult();
        pr.setPredictionDate("2026-02-01");
        pr.setPredictedValue(60.0);

        PredictionAlgorithm algo = fakeAlgorithm("LINEAR_REGRESSION", "线性回归", trained, List.of(pr));
        when(modelVersionMapper.selectByModelName("predict_model")).thenReturn(new ArrayList<>());
        when(modelVersionMapper.insert(any(ModelVersion.class))).thenAnswer(inv -> {
            ((ModelVersion) inv.getArgument(0)).setId(99L);
            return 1;
        });

        // Make the mock executor run the runnable synchronously so executePredictionAsync executes
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        EnhancedPredictionService service = createService(List.of(algo));
        service.runPrediction(1L);

        // Verify task was completed (RUNNING + COMPLETED = at least 2 updates)
        verify(predictionTaskMapper, atLeast(2)).updateById(any(PredictionTask.class));
        verify(modelVersionMapper).insert(any(ModelVersion.class));
        verify(predictionResultMapper, atLeast(1)).insert(any(PredictionResult.class));
    }

    @Test
    @DisplayName("runPrediction - insufficient data in async sets FAILED status")
    void runPrediction_asyncInsufficientData() {
        PredictionTask t = task(1L, 1L, "date", "sales");
        when(predictionTaskMapper.selectById(1L)).thenReturn(t);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(1L));
        when(dataReader.readRows(any())).thenReturn(rows("date", "sales",
                "2026-01-01", "10"));  // only 1 point - insufficient

        // Make the mock executor run the runnable synchronously so executePredictionAsync executes
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        EnhancedPredictionService service = createService(new ArrayList<>());
        service.runPrediction(1L);

        // Task should be marked FAILED (RUNNING + FAILED = at least 2 updates)
        verify(predictionTaskMapper, atLeast(2)).updateById(any(PredictionTask.class));
    }
}
