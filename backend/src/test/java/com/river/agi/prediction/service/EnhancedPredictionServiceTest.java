package com.river.agi.prediction.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.river.agi.dataset.entity.Dataset;
import com.river.agi.dataset.mapper.DatasetMapper;
import com.river.agi.dataset.service.DatasetDataReaderService;
import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.ModelVersionMapper;
import com.river.agi.prediction.mapper.PredictionResultMapper;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.common.SecurityUtils;
import com.river.agi.common.ResourceAccessValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

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

    @Test
    @DisplayName("Should return prediction task count")
    void getPredictionTaskCount() {
        when(predictionTaskMapper.selectCount(any())).thenReturn(10L);

        EnhancedPredictionService service = createService();
        long count = service.getPredictionTaskCount();
        assertEquals(10L, count);
    }

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

        EnhancedPredictionService service = createService();
        Map<String, Object> comparison = service.compareModelVersions(1L, 2L);
        assertNotNull(comparison);
        assertEquals("model2", comparison.get("recommendation"));
    }

    @Test
    @DisplayName("Should throw exception when model not found in comparison")
    void compareModelVersions_notFound() {
        when(modelVersionMapper.selectById(anyLong())).thenReturn(null);

        EnhancedPredictionService service = createService();
        assertThrows(Exception.class, () -> service.compareModelVersions(1L, 2L));
    }

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

        EnhancedPredictionService service = createService();
        assertDoesNotThrow(() -> service.rollbackToModelVersion(1L, 5L));
        verify(predictionTaskMapper).updateById(task);
    }

    @Test
    @DisplayName("Should handle model version with null metrics in comparison")
    void compareModelVersions_nullMetrics() {
        ModelVersion model1 = new ModelVersion();
        model1.setId(1L);
        model1.setModelName("test");
        model1.setModelType("LINEAR_REGRESSION");
        model1.setVersionNumber(1);

        ModelVersion model2 = new ModelVersion();
        model2.setId(2L);
        model2.setModelName("test");
        model2.setModelType("EXPONENTIAL_SMOOTHING");
        model2.setVersionNumber(2);

        when(modelVersionMapper.selectById(1L)).thenReturn(model1);
        when(modelVersionMapper.selectById(2L)).thenReturn(model2);

        EnhancedPredictionService service = createService();
        Map<String, Object> comparison = service.compareModelVersions(1L, 2L);
        assertNotNull(comparison);
    }

    @Test
    @DisplayName("Should handle null rollback target")
    void rollbackToModelVersion_targetNotFound() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(modelVersionMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService();
        assertThrows(Exception.class, () -> service.rollbackToModelVersion(1L, 999L));
    }

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

        EnhancedPredictionService service = createService();
        assertNotNull(service.getPredictionTasks(1, 10));
    }

    @Test
    @DisplayName("Should return prediction results by task ID")
    void getPredictionResults() {
        PredictionResult result = new PredictionResult();
        result.setTaskId(1L);
        result.setPredictedValue(100.0);

        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(result));

        EnhancedPredictionService service = createService();
        List<PredictionResult> results = service.getPredictionResults(1L);
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Should handle empty prediction results")
    void getPredictionResults_empty() {
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService();
        List<PredictionResult> results = service.getPredictionResults(1L);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should get model versions by name")
    void getModelVersions() {
        ModelVersion version = new ModelVersion();
        version.setId(1L);
        version.setModelName("test_model");

        when(modelVersionMapper.selectByModelName("test_model")).thenReturn(List.of(version));

        EnhancedPredictionService service = createService();
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

        EnhancedPredictionService service = createService();
        ModelVersion result = service.getModelVersion(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw when model version not found")
    void getModelVersion_notFound() {
        when(modelVersionMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService();
        assertThrows(Exception.class, () -> service.getModelVersion(999L));
    }

    @Test
    @DisplayName("Should get prediction task by ID")
    void getPredictionTask() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);

        EnhancedPredictionService service = createService();
        PredictionTask result = service.getPredictionTask(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should throw when prediction task not found")
    void getPredictionTask_notFound() {
        when(predictionTaskMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService();
        assertThrows(Exception.class, () -> service.getPredictionTask(999L));
    }

    @Test
    @DisplayName("Should get prediction metrics with model version")
    void getPredictionMetrics_withModel() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setStatus("COMPLETED");
        task.setTargetField("sales");
        task.setModelVersionId(10L);

        ModelVersion version = new ModelVersion();
        version.setId(10L);
        version.setMae(3.5);
        version.setRmse(7.2);
        version.setMape(4.1);

        PredictionResult result = new PredictionResult();
        result.setPredictionDate("2026-01-01");
        result.setPredictedValue(100.0);
        result.setLowerBound(90.0);
        result.setUpperBound(110.0);
        result.setConfidence(0.9);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(modelVersionMapper.selectById(10L)).thenReturn(version);
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(List.of(result));

        EnhancedPredictionService service = createService();
        Map<String, Object> metrics = service.getPredictionMetrics(1L);
        assertNotNull(metrics);
        assertEquals(3.5, metrics.get("mae"));
        assertEquals(7.2, metrics.get("rmse"));
        assertEquals(4.1, metrics.get("mape"));
    }

    @Test
    @DisplayName("Should throw when getting metrics for non-existent task")
    void getPredictionMetrics_taskNotFound() {
        when(predictionTaskMapper.selectById(999L)).thenReturn(null);

        EnhancedPredictionService service = createService();
        assertThrows(Exception.class, () -> service.getPredictionMetrics(999L));
    }

    @Test
    @DisplayName("Should detect prediction bias")
    void detectPredictionBias() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setStatus("COMPLETED");
        task.setDatasetId(100L);
        task.setTimeField("date");
        task.setTargetField("sales");

        Dataset dataset = new Dataset();
        dataset.setId(100L);

        when(predictionTaskMapper.selectById(1L)).thenReturn(task);
        when(datasetMapper.selectById(100L)).thenReturn(dataset);
        when(dataReader.readRows(dataset)).thenReturn(new ArrayList<>());
        when(predictionResultMapper.selectByTaskId(1L)).thenReturn(new ArrayList<>());

        EnhancedPredictionService service = createService();
        Map<String, Object> bias = service.detectPredictionBias(1L);
        assertNotNull(bias);
        assertEquals(1L, bias.get("taskId"));
    }

    @Test
    @DisplayName("Should retrieve available algorithms")
    void getAvailableAlgorithms() {
        PredictionAlgorithm algo = new PredictionAlgorithm() {
            @Override
            public String getAlgorithmType() { return "TEST_ALGO"; }
            @Override
            public String getAlgorithmName() { return "测试算法"; }
            @Override
            public ModelVersion train(PredictionTask task, List<PredictionData.SeriesPoint> series) {
                return new ModelVersion();
            }
            @Override
            public List<PredictionResult> predict(PredictionTask task, List<PredictionData.SeriesPoint> series,
                                                  ModelVersion modelVersion, int forecastDays) {
                return new ArrayList<>();
            }
            @Override
            public Map<String, Object> getAlgorithmParams(PredictionTask task) {
                return Map.of();
            }
        };

        EnhancedPredictionService service = createService(List.of(algo));
        List<Map<String, Object>> algorithms = service.getAvailableAlgorithms();
        assertNotNull(algorithms);
        assertFalse(algorithms.isEmpty());
    }

    private EnhancedPredictionService createService() {
        return createService(new ArrayList<>());
    }

    private EnhancedPredictionService createService(List<PredictionAlgorithm> algorithms) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new EnhancedPredictionService(
                predictionTaskMapper, modelVersionMapper, predictionResultMapper,
                datasetMapper, objectMapper, securityUtils, accessValidator, dataReader, algorithms
        );
    }
}

