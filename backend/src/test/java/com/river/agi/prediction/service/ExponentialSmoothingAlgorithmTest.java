package com.river.agi.prediction.service;

import com.river.agi.prediction.entity.ModelVersion;
import com.river.agi.prediction.entity.PredictionResult;
import com.river.agi.prediction.entity.PredictionTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialSmoothingAlgorithmTest {

    private ExponentialSmoothingAlgorithm algorithm;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        algorithm = new ExponentialSmoothingAlgorithm(objectMapper);
    }

    @Test
    @DisplayName("Should return correct algorithm name and type")
    void getAlgorithmInfo() {
        assertEquals("指数平滑预测", algorithm.getAlgorithmName());
        assertEquals("EXPONENTIAL_SMOOTHING", algorithm.getAlgorithmType());
    }

    @Test
    @DisplayName("Should return algorithm params")
    void getAlgorithmParams() {
        PredictionTask task = new PredictionTask();
        Map<String, Object> params = algorithm.getAlgorithmParams(task);

        assertNotNull(params);
        assertEquals("EXPONENTIAL_SMOOTHING", params.get("algorithm"));
        assertEquals("考虑近期数据权重的平滑预测", params.get("description"));
        assertNotNull(params.get("alpha"));
    }

    @Test
    @DisplayName("Should train model with sufficient data and optimize alpha")
    void train_sufficientData() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 5 + Math.sin(i / 7.0) * 20));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertEquals("EXPONENTIAL_SMOOTHING", version.getModelType());
        assertEquals("EXPONENTIAL_SMOOTHING", version.getAlgorithmType());
        assertEquals("TIME_SERIES", version.getTaskType());
        assertEquals("demand_prediction_ets_10", version.getModelName());
        assertEquals(1, version.getVersionNumber());
        assertEquals("ACTIVE", version.getStatus());
        assertEquals(30L, version.getTrainingSamples());
        assertEquals(1L, version.getPredictionTaskId());
        assertNotNull(version.getMae());
        assertNotNull(version.getRmse());
        assertNotNull(version.getMape());
        assertTrue(version.getMae() >= 0);
        assertTrue(version.getRmse() >= 0);
        assertTrue(version.getMape() >= 0);
        assertTrue(version.getTrainingMetricsJson().contains("EXPONENTIAL_SMOOTHING"));
        assertTrue(version.getTrainingMetricsJson().contains("alpha"));
        assertTrue(version.getTrainingMetricsJson().contains("seasonalPeriod"));
        assertNotNull(version.getFeatureImportanceJson());
        assertTrue(version.getFeatureImportanceJson().contains("exponential_smoothing"));
        assertNotNull(version.getCreatedAt());
    }

    @Test
    @DisplayName("Should handle single-point series (validCount = 0 path)")
    void train_singlePoint() {
        PredictionTask task = new PredictionTask();
        task.setId(2L);
        task.setDatasetId(2L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        series.add(new PredictionData.SeriesPoint(LocalDate.now(), 50.0));

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertEquals(0.0, version.getMae());
        assertEquals(0.0, version.getRmse());
        assertEquals(0.0, version.getMape());
        assertNotNull(version.getTrainingMetricsJson());
    }

    @Test
    @DisplayName("Should handle two-point series")
    void train_twoPoints() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(3L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        series.add(new PredictionData.SeriesPoint(LocalDate.now().minusDays(1), 100.0));
        series.add(new PredictionData.SeriesPoint(LocalDate.now(), 120.0));

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertTrue(version.getMae() >= 0);
        assertNotNull(version.getTrainingMetricsJson());
    }

    @Test
    @DisplayName("Should handle series with zero actual values (mape branch)")
    void train_zeroActualValues() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(4L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(10);
        for (int i = 0; i < 10; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), i == 5 ? 0.0 : 100.0 + i));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertTrue(version.getMape() >= 0);
    }

    @Test
    @DisplayName("Should generate predictions with trained model metrics")
    void predict_withTrainedMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 5 + Math.sin(i / 7.0) * 20));
        }

        ModelVersion version = algorithm.train(task, series);
        List<PredictionResult> predictions = algorithm.predict(task, series, version, 7);

        assertNotNull(predictions);
        assertEquals(7, predictions.size());
        for (PredictionResult p : predictions) {
            assertNotNull(p.getPredictionDate());
            assertNotNull(p.getPredictedValue());
            assertNotNull(p.getLowerBound());
            assertNotNull(p.getUpperBound());
            assertNotNull(p.getConfidence());
            assertTrue(p.getPredictedValue() >= 0);
            assertTrue(p.getLowerBound() <= p.getPredictedValue());
            assertTrue(p.getUpperBound() >= p.getPredictedValue());
            assertTrue(p.getConfidence() > 0 && p.getConfidence() <= 1.0);
            assertEquals(1L, p.getTaskId());
            assertNotNull(p.getCreatedAt());
        }
    }

    @Test
    @DisplayName("Should use default alpha when metrics missing alpha key")
    void predict_missingAlphaInMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = new ModelVersion();
        version.setTrainingMetricsJson("{\"mae\":1.0}");
        version.setModelType("EXPONENTIAL_SMOOTHING");

        List<PredictionResult> predictions = algorithm.predict(task, series, version, 5);

        assertNotNull(predictions);
        assertEquals(5, predictions.size());
    }

    @Test
    @DisplayName("Should handle null metrics JSON gracefully")
    void predict_nullMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = new ModelVersion();
        version.setTrainingMetricsJson(null);
        version.setModelType("EXPONENTIAL_SMOOTHING");

        List<PredictionResult> predictions = algorithm.predict(task, series, version, 5);

        assertNotNull(predictions);
        assertEquals(5, predictions.size());
    }

    @Test
    @DisplayName("Should handle invalid metrics JSON gracefully")
    void predict_invalidMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = new ModelVersion();
        version.setTrainingMetricsJson("invalid-json");
        version.setModelType("EXPONENTIAL_SMOOTHING");

        assertDoesNotThrow(() -> algorithm.predict(task, series, version, 5));
    }

    @Test
    @DisplayName("Should produce zero-variance confidence when constant series")
    void predict_constantSeries_confidenceOne() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(20);
        for (int i = 0; i < 20; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0));
        }

        ModelVersion version = algorithm.train(task, series);
        List<PredictionResult> predictions = algorithm.predict(task, series, version, 3);

        assertNotNull(predictions);
        assertEquals(3, predictions.size());
        // constant series => zero variance => confidence 1.0
        for (PredictionResult p : predictions) {
            assertEquals(1.0, p.getConfidence());
        }
    }

    @Test
    @DisplayName("Should produce metrics in training output")
    void train_producesMetrics() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i * 3));
        }

        ModelVersion version = algorithm.train(task, series);

        assertTrue(version.getMae() >= 0);
        assertTrue(version.getRmse() >= 0);
        assertTrue(version.getMape() >= 0);
        assertNotNull(version.getFeatureImportanceJson());
    }
}
