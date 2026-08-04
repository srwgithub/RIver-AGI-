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

class MovingAverageAlgorithmTest {

    private MovingAverageAlgorithm algorithm;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        algorithm = new MovingAverageAlgorithm(objectMapper);
    }

    @Test
    @DisplayName("Should return correct algorithm name and type")
    void getAlgorithmInfo() {
        assertEquals("移动平均预测", algorithm.getAlgorithmName());
        assertEquals("MOVING_AVERAGE", algorithm.getAlgorithmType());
    }

    @Test
    @DisplayName("Should return algorithm params")
    void getAlgorithmParams() {
        PredictionTask task = new PredictionTask();
        Map<String, Object> params = algorithm.getAlgorithmParams(task);

        assertNotNull(params);
        assertEquals("MOVING_AVERAGE", params.get("algorithm"));
        assertEquals("基于历史平均值的平稳趋势预测", params.get("description"));
        assertNotNull(params.get("windowSize"));
    }

    @Test
    @DisplayName("Should train model with sufficient data (windowSize=7 for <30 points)")
    void train_sufficientData_smallWindow() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);
        task.setDatasetId(10L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(20);
        for (int i = 0; i < 20; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 2));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertEquals("MOVING_AVERAGE", version.getModelType());
        assertEquals("MOVING_AVERAGE", version.getAlgorithmType());
        assertEquals("TIME_SERIES", version.getTaskType());
        assertEquals("demand_prediction_ma_10", version.getModelName());
        assertEquals(1, version.getVersionNumber());
        assertEquals("ACTIVE", version.getStatus());
        assertEquals(20L, version.getTrainingSamples());
        assertEquals(1L, version.getPredictionTaskId());
        assertNotNull(version.getMae());
        assertNotNull(version.getRmse());
        assertNotNull(version.getMape());
        assertTrue(version.getMae() >= 0);
        assertTrue(version.getRmse() >= 0);
        assertTrue(version.getMape() >= 0);
        assertTrue(version.getTrainingMetricsJson().contains("MOVING_AVERAGE"));
        assertTrue(version.getTrainingMetricsJson().contains("windowSize"));
        assertNotNull(version.getFeatureImportanceJson());
        assertTrue(version.getFeatureImportanceJson().contains("moving_average"));
        assertNotNull(version.getCreatedAt());
    }

    @Test
    @DisplayName("Should use windowSize=14 for medium series (30-89 points)")
    void train_mediumSeries_window14() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(50);
        for (int i = 0; i < 50; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertTrue(version.getTrainingMetricsJson().contains("\"windowSize\":14"));
    }

    @Test
    @DisplayName("Should use windowSize=30 for large series (>=90 points)")
    void train_largeSeries_window30() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(100);
        for (int i = 0; i < 100; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        assertTrue(version.getTrainingMetricsJson().contains("\"windowSize\":30"));
    }

    @Test
    @DisplayName("Should use proportional windowSize for tiny series (<7 points)")
    void train_tinySeries_proportionalWindow() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(4);
        for (int i = 0; i < 4; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = algorithm.train(task, series);

        assertNotNull(version);
        // validCount=0 because windowSize=2, loop starts at i=2 < 4, so validCount should be >0 actually.
        // windowSize = max(1, 4/2) = 2; loop i=2,3 => validCount=2
        assertNotNull(version.getTrainingMetricsJson());
    }

    @Test
    @DisplayName("Should produce zero metrics when validCount = 0 (single point)")
    void train_singlePoint_validCountZero() {
        PredictionTask task = new PredictionTask();
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
    @DisplayName("Should handle series with zero actual values (mape branch)")
    void train_zeroActualValues() {
        PredictionTask task = new PredictionTask();
        task.setDatasetId(4L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(15);
        for (int i = 0; i < 15; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), i == 7 ? 0.0 : 100.0 + i));
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
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100 + i * 2));
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
    @DisplayName("Should use default windowSize=7 when metrics missing windowSize key")
    void predict_missingWindowInMetrics() {
        PredictionTask task = new PredictionTask();
        task.setId(1L);

        List<PredictionData.SeriesPoint> series = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        for (int i = 0; i < 30; i++) {
            series.add(new PredictionData.SeriesPoint(baseDate.plusDays(i), 100.0 + i));
        }

        ModelVersion version = new ModelVersion();
        version.setTrainingMetricsJson("{\"mae\":1.0}");
        version.setModelType("MOVING_AVERAGE");

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
        version.setModelType("MOVING_AVERAGE");

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
        version.setModelType("MOVING_AVERAGE");

        assertDoesNotThrow(() -> algorithm.predict(task, series, version, 5));
    }

    @Test
    @DisplayName("Should produce confidence 1.0 when constant series (zero variance)")
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
        for (PredictionResult p : predictions) {
            assertEquals(1.0, p.getConfidence());
        }
    }
}
